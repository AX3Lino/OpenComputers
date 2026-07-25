package li.cil.oc.server.component

import java.util

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.networking.security.IActionHost
import appeng.api.networking.security.MachineSource
import appeng.api.storage.data.IAEFluidStack
import appeng.api.storage.data.IAEItemStack
import appeng.me.GridAccessException
import appeng.me.helpers.AENetworkProxy
import appeng.me.helpers.IGridProxyable
import appeng.util.Platform
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.InventoryUtils
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.fluids.FluidContainerRegistry
import net.minecraftforge.fluids.FluidStack

import scala.collection.convert.WrapAsJava._

// A Transposer whose seventh, virtual side is an ME network, for both items and fluids.
object METransposer {

  abstract class Common extends Transposer.Common {
    override val node = api.Network.newNode(this, Visibility.Network).
      withComponent("me_transposer").
      withConnector().
      create()

    protected def proxy: Option[AENetworkProxy]

    protected def actionHost: IActionHost

    private final lazy val meDeviceInfo = Map(
      DeviceAttribute.Class -> DeviceClass.Generic,
      DeviceAttribute.Description -> "ME Transposer",
      DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product -> "TP-ME1"
    )

    override def getDeviceInfo: util.Map[String, String] = meDeviceInfo

    override def onTransferContents(): Option[String] = {
      if (node.tryChangeBuffer(-Settings.get.meTransposerCost)) None
      else Option("not enough energy")
    }

    private def pauseForFluid(context: Context, moved: Int) {
      val delay = moved.toDouble / Settings.get.transposerFluidTransferRate.toDouble - 0.05
      if (delay > 0) context.pause(delay)
    }

    // ----------------------------------------------------------------------- //
    // The virtual ME side.

    override protected def isVirtualSide(args: Arguments, index: Int): Boolean =
      (args.isString(index) && args.checkString(index).equalsIgnoreCase("me")) ||
        (args.isInteger(index) && args.checkInteger(index) == ForgeDirection.UNKNOWN.ordinal)

    override protected def virtualSideBothError = "source and sink cannot both be the ME network"

    @Callback(doc = """function():boolean -- Get whether the device is actively connected to an ME network (powered and got a channel).""")
    def isMeConnected(context: Context, args: Arguments): Array[AnyRef] = result(proxy.exists(_.isActive))

    // ----------------------------------------------------------------------- //
    // Filter parsing for ME requests: a descriptor table or a database address+entry index, at argument index 2 or 3.

    protected def filterIndex(args: Arguments): Int =
      Seq(2, 3).find(i => i < args.count && (args.isTable(i) || args.isString(i))).getOrElse(-1)

    /** Looks up the item stack an upgrade database entry (address at `offset`, slot at `offset + 1`) represents. */
    protected def stackFromDatabase(args: Arguments, offset: Int): Option[ItemStack] =
      node.network.node(args.checkString(offset)) match {
        case component: Component => component.host match {
          case database: UpgradeDatabase =>
            val entry = args.checkSlot(database.data, offset + 1)
            Option(database.data.getStackInSlot(entry)).map(_.copy())
          case _ => None
        }
        case _ => None
      }

    /** Returns the parsed stack (or null) and the argument index following the filter. */
    protected def parseItemFilter(args: Arguments, offset: Int, amount: Int): (IAEItemStack, Int) = {
      if (args.isTable(offset)) {
        val stack = Option(AEStackFactory.parseItem(args.checkTable(offset))).map { s =>
          s.setStackSize(amount)
          s
        }.orNull
        (stack, offset + 1)
      }
      else {
        val stack = stackFromDatabase(args, offset).map { s =>
          val aes = AEApi.instance.storage.createItemStack(s)
          aes.setStackSize(amount)
          aes
        }.orNull
        (stack, offset + 2)
      }
    }

    private def parseFluidFilter(args: Arguments, offset: Int, amount: Int): IAEFluidStack = {
      if (args.isTable(offset)) {
        Option(AEStackFactory.parseFluid(args.checkTable(offset))).map { s =>
          s.setStackSize(amount)
          s
        }.orNull
      }
      else {
        stackFromDatabase(args, offset).flatMap { s =>
          Option(FluidContainerRegistry.getFluidForFilledItem(s))
        }.map { fluid =>
          fluid.amount = amount
          AEApi.instance.storage.createFluidStack(fluid)
        }.orNull
      }
    }

    // ----------------------------------------------------------------------- //
    // Item transfers.

    // Overridden only to attach ME-specific doc text; the virtual-side dispatch itself lives in InventoryTransfer.
    @Callback(doc = """function(sourceSide, sinkSide[, count:number[, sourceSlot:number[, sinkSlot:number]]]):number -- Transfer some items between two inventories. Either side may also be the string "me" (or 6) for the ME network; pulling from ME requires a filter (table or dbAddress:string, dbEntry:number) in place of sourceSlot.""")
    override def transferItem(context: Context, args: Arguments): Array[AnyRef] = super.transferItem(context, args)

    override protected def transferItemVirtual(context: Context, args: Arguments, sourceIsMe: Boolean): Array[AnyRef] =
      if (sourceIsMe) transferItemFromMe(args) else transferItemToMe(args)

    private def transferItemFromMe(args: Arguments): Array[AnyRef] = {
      val sinkSide = checkSideForAction(args, 1)
      val sinkPos = position.offset(sinkSide)
      val filterAt = filterIndex(args)
      if (filterAt < 0) return result(Unit, "filter required when pulling from the ME network")
      val count = if (filterAt > 2) args.optItemCount(2) else 64

      val (request, nextIndex) = parseItemFilter(args, filterAt, count)
      if (request == null) return result(Unit, "invalid filter")

      val inventory = InventoryUtils.inventoryAt(sinkPos).getOrElse(return result(Unit, "no inventory"))
      val sinkSlot = args.optSlot(inventory, nextIndex, -1)

      val p = proxy.getOrElse(return result(Unit, "no ME network"))
      try {
        if (!p.isActive) return result(Unit, "no ME network")
        val storage = p.getStorage.getItemInventory
        val energy = p.getEnergy
        val source = new MachineSource(actionHost)

        // Simulate insertion first, then extract from the network and commit; any leftover goes back to the network.
        val simulated = request.getItemStack
        val fits =
          if (sinkSlot < 0) InventoryUtils.insertIntoInventory(simulated, inventory, Option(sinkSide.getOpposite), count, simulate = true)
          else InventoryUtils.insertIntoInventorySlot(simulated, inventory, Option(sinkSide.getOpposite), sinkSlot, count, simulate = true)
        if (!fits) return result(0)

        request.setStackSize(count - simulated.stackSize)
        val extracted = Platform.poweredExtraction(energy, storage, request, source)
        if (extracted == null || extracted.getStackSize == 0) return result(0)

        val stack = extracted.getItemStack
        var moved = stack.stackSize
        if (sinkSlot < 0) InventoryUtils.insertIntoInventory(stack, inventory, Option(sinkSide.getOpposite))
        else InventoryUtils.insertIntoInventorySlot(stack, inventory, Option(sinkSide.getOpposite), sinkSlot)
        if (stack.stackSize > 0) {
          moved -= stack.stackSize
          val leftover = extracted.copy()
          leftover.setStackSize(stack.stackSize)
          storage.injectItems(leftover, Actionable.MODULATE, source)
        }
        result(moved)
      }
      catch {
        case _: GridAccessException => result(Unit, "no ME network")
      }
    }

    private def transferItemToMe(args: Arguments): Array[AnyRef] = {
      val sourceSide = checkSideForAction(args, 0)
      val sourcePos = position.offset(sourceSide)
      val count = args.optItemCount(2)
      val inventory = InventoryUtils.inventoryAt(sourcePos).getOrElse(return result(Unit, "no inventory"))
      val sourceSlot = args.optSlot(inventory, 3, -1)

      val p = proxy.getOrElse(return result(Unit, "no ME network"))
      try {
        if (!p.isActive) return result(Unit, "no ME network")
        val storage = p.getStorage.getItemInventory
        val energy = p.getEnergy
        val source = new MachineSource(actionHost)

        val consumer = (stack: ItemStack) => {
          val leftover = Platform.poweredInsert(energy, storage, AEApi.instance.storage.createItemStack(stack), source)
          stack.stackSize = if (leftover == null) 0 else leftover.getStackSize.toInt
        }
        val moved =
          if (sourceSlot < 0) InventoryUtils.extractAnyFromInventory(consumer, inventory, sourceSide.getOpposite, count)
          else InventoryUtils.extractFromInventorySlot(consumer, inventory, sourceSide.getOpposite, sourceSlot, count)
        result(moved)
      }
      catch {
        case _: GridAccessException => result(Unit, "no ME network")
      }
    }

    // ----------------------------------------------------------------------- //
    // Fluid transfers.

    // Overridden only to attach ME-specific doc text; the virtual-side dispatch itself lives in InventoryTransfer.
    @Callback(doc = """function(sourceSide, sinkSide[, count:number[, sourceTank:number]]):boolean, number -- Transfer some fluid between two tanks. Either side may also be the string "me" (or 6) for the ME network; pulling from ME requires a filter (table or dbAddress:string, dbEntry:number). Returns operation result and filled amount""")
    override def transferFluid(context: Context, args: Arguments): Array[AnyRef] = super.transferFluid(context, args)

    override protected def transferFluidVirtual(context: Context, args: Arguments, sourceIsMe: Boolean): Array[AnyRef] =
      if (sourceIsMe) transferFluidFromMe(context, args) else transferFluidToMe(context, args)

    private def transferFluidFromMe(context: Context, args: Arguments): Array[AnyRef] = {
      val sinkSide = checkSideForAction(args, 1)
      val sinkPos = position.offset(sinkSide)
      val filterAt = filterIndex(args)
      if (filterAt < 0) return result(Unit, "filter required when pulling from the ME network")
      val count = if (filterAt > 2) args.optFluidCount(2) else FluidContainerRegistry.BUCKET_VOLUME

      val request = parseFluidFilter(args, filterAt, count)
      if (request == null) return result(Unit, "invalid filter")

      val handler = FluidUtils.fluidHandlerAt(sinkPos).getOrElse(return result(Unit, "no tank"))

      val p = proxy.getOrElse(return result(Unit, "no ME network"))
      try {
        if (!p.isActive) return result(Unit, "no ME network")
        val storage = p.getStorage.getFluidInventory
        val energy = p.getEnergy
        val source = new MachineSource(actionHost)

        val simulated = request.getFluidStack
        val fits = handler.fill(sinkSide.getOpposite, simulated, false)
        if (fits <= 0) return result(false, 0)

        request.setStackSize(fits)
        val extracted = Platform.poweredExtraction(energy, storage, request, source)
        if (extracted == null || extracted.getStackSize == 0) return result(false, 0)

        val stack = extracted.getFluidStack
        val filled = handler.fill(sinkSide.getOpposite, stack, true)
        if (filled < stack.amount) {
          val leftover = extracted.copy()
          leftover.setStackSize(stack.amount - filled)
          storage.injectItems(leftover, Actionable.MODULATE, source)
        }
        pauseForFluid(context, filled)
        result(filled > 0, filled)
      }
      catch {
        case _: GridAccessException => result(Unit, "no ME network")
      }
    }

    private def transferFluidToMe(context: Context, args: Arguments): Array[AnyRef] = {
      val sourceSide = checkSideForAction(args, 0)
      val sourcePos = position.offset(sourceSide)
      val count = args.optFluidCount(2)
      val sourceTank = args.optInteger(3, -1)
      val handler = FluidUtils.fluidHandlerAt(sourcePos).getOrElse(return result(Unit, "no tank"))
      val drainSide = sourceSide.getOpposite

      val p = proxy.getOrElse(return result(Unit, "no ME network"))
      try {
        if (!p.isActive) return result(Unit, "no ME network")
        val storage = p.getStorage.getFluidInventory
        val energy = p.getEnergy
        val source = new MachineSource(actionHost)

        val simulated =
          if (sourceTank < 0) handler.drain(drainSide, count, false)
          else {
            val info = handler.getTankInfo(drainSide)
            if (info == null || sourceTank >= info.length || info(sourceTank).fluid == null) null
            else {
              val fluid = info(sourceTank).fluid.copy()
              fluid.amount = count
              handler.drain(drainSide, fluid, false)
            }
          }
        if (simulated == null || simulated.amount <= 0) return result(false, 0)

        val leftover = Platform.poweredInsert(energy, storage, AEApi.instance.storage.createFluidStack(simulated.copy()), source)
        val accepted = simulated.amount - (if (leftover == null) 0 else leftover.getStackSize.toInt)
        if (accepted <= 0) return result(false, 0)

        handler.drain(drainSide, new FluidStack(simulated.getFluid, accepted), true)
        pauseForFluid(context, accepted)
        result(true, accepted)
      }
      catch {
        case _: GridAccessException => result(Unit, "no ME network")
      }
    }
  }

  // Shared by every block-hosted flavor with an AE2 grid proxy on the tile entity itself (METransposer and friends).
  trait GridHost extends Common {
    def host: IGridProxyable with IActionHost

    override protected def proxy = Some(host.getProxy)

    override protected def actionHost: IActionHost = host
  }

  // Shared by every microcontroller-upgrade flavor; hosts without an AE2 grid connection just report "no ME network".
  trait GridUpgradeHost extends Common {
    def host: EnvironmentHost

    node.setVisibility(Visibility.Neighbors)

    override def position = BlockPosition(host)

    // Option(...), not Some(...): getProxy legitimately returns a Java null when no ME card is
    // installed, and Some(null) would be mistaken for a present-but-unusable proxy downstream.
    override protected def proxy = host match {
      case p: IGridProxyable => Option(p.getProxy)
      case _ => None
    }

    override protected def actionHost: IActionHost = host.asInstanceOf[IActionHost]
  }

  /** Hosted by the ME Transposer block's own tile entity. */
  class Block(val host: tileentity.METransposer) extends Common with Transposer.BlockHost with GridHost

  class Upgrade(val host: EnvironmentHost) extends Common with GridUpgradeHost
}

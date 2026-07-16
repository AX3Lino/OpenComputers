package li.cil.oc.server.component

import java.util

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.networking.security.IActionHost
import appeng.api.networking.security.MachineSource
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
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.item.data.TransposerData
import li.cil.oc.common.tileentity
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection

import scala.collection.convert.WrapAsJava._

// A Transposer whose seventh, virtual side is an ME network, for items only.
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

    // Fluid transfer rate for an upgrade card: NBT tag on the matching card ItemStack, mirroring vanilla Transposer.Upgrade.
    protected def upgradeFluidTransferRate(host: EnvironmentHost, cardBlockName: String): Int = host match {
      case microcontroller: tileentity.Microcontroller =>
        microcontroller.info.components.find(_.isItemEqual(api.Items.get(cardBlockName).createItemStack(1)))
          .filter(_.hasTagCompound)
          .map(_.getTagCompound)
          .filter(_.hasKey(TransposerData.FLUID_TRANSFER_RATE))
          .map(_.getInteger(TransposerData.FLUID_TRANSFER_RATE))
          .getOrElse(Settings.get.transposerFluidTransferRate)
      case _ => 0
    }

    // ----------------------------------------------------------------------- //
    // The virtual ME side.

    override protected def isVirtualSide(args: Arguments, index: Int): Boolean =
      (args.isString(index) && args.checkString(index).equalsIgnoreCase("me")) ||
        (args.isInteger(index) && args.checkInteger(index) == ForgeDirection.UNKNOWN.ordinal)

    override protected def virtualSideBothError = "source and sink cannot both be the ME network"

    override protected def virtualSideSwapError = "cannot swap with the ME network"

    @Callback(doc = """function():boolean -- Get whether the device is actively connected to an ME network (powered and got a channel).""")
    def isMeConnected(context: Context, args: Arguments): Array[AnyRef] = result(proxy.exists(_.isActive))

    // ----------------------------------------------------------------------- //
    // Filter parsing for ME requests: a descriptor table or a database address+entry index, at argument index 2 or 3.

    protected def filterIndex(args: Arguments): Int =
      Seq(2, 3).find(i => i < args.count && (args.isTable(i) || args.isString(i))).getOrElse(-1)

    /** Returns the parsed stack (or null) and the argument index following the filter. */
    protected def parseItemFilter(args: Arguments, offset: Int, amount: Int): (IAEItemStack, Int) = {
      if (args.isTable(offset)) {
        val stack = Option(AEStackFactory.parse[IAEItemStack](args.checkTable(offset))).map { s =>
          s.setStackSize(amount)
          s
        }.orNull
        (stack, offset + 1)
      }
      else {
        val stack = Option(DatabaseAccess.getStackFromDatabase(node, args, offset)).map { s =>
          val aes = AEApi.instance.storage.createItemStack(s)
          aes.setStackSize(amount)
          aes
        }.orNull
        (stack, offset + 2)
      }
    }

    // ----------------------------------------------------------------------- //
    // Item transfers.

    // Overridden only to attach ME-specific doc text; the virtual-side dispatch itself lives in InventoryTransfer.
    @Callback(doc = """function(sourceSide, sinkSide[, count:number[, sourceSlot:number[, sinkSlot:number]]]):number -- Transfer some items between two inventories. Either side may also be the string "me" (or 6) for the ME network; pulling from ME requires a filter (table or dbAddress:string, dbEntry:number) in place of sourceSlot.""")
    override def transferItem(context: Context, args: Arguments): Array[AnyRef] = super.transferItem(context, args)

    @Callback(doc = """function(sourceSide:number, sinkSide:number, sourceSlot:number, sinkSlot:number[, safe:boolean]):boolean -- Swap two inventory slots if and only if both directions succeed. Safe swaps require two non-empty slots. The ME network cannot take part in swaps.""")
    override def swap(context: Context, args: Arguments): Array[AnyRef] = super.swap(context, args)

    override protected def transferItemVirtual(context: Context, args: Arguments, sourceIsMe: Boolean): Array[AnyRef] =
      if (sourceIsMe) transferItemFromMe(args) else transferItemToMe(args)

    override protected def transferFluidVirtual(context: Context, args: Arguments, sourceIsMe: Boolean): Array[AnyRef] =
      result(Unit, "this device cannot transfer fluids to or from the ME network")

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

    override protected def proxy = host match {
      case p: IGridProxyable => Some(p.getProxy)
      case _ => None
    }

    override protected def actionHost: IActionHost = host.asInstanceOf[IActionHost]
  }

  /** Hosted by the ME Transposer block's own tile entity. */
  class Block(val host: tileentity.METransposer) extends Common with Transposer.BlockHost with GridHost

  class Upgrade(val host: EnvironmentHost) extends Common with GridUpgradeHost {
    override def fluidTransferRate(): Int = upgradeFluidTransferRate(host, Constants.BlockName.METransposer)
  }
}

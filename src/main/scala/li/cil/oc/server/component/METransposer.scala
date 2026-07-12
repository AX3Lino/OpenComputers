package li.cil.oc.server.component

import java.util

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.networking.security.MachineSource
import appeng.api.storage.data.IAEItemStack
import appeng.me.GridAccessException
import appeng.util.Platform
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.common.tileentity
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection

import scala.collection.convert.WrapAsJava._

/**
 * A Transposer whose seventh, virtual side is an ME network, for items only.
 * All transposer callbacks are inherited unchanged; the item transfer
 * callback additionally accepts the string "me" (or the side number 6) as
 * source or sink. See MEDualTransposer for the fluid-capable variant.
 *
 * Deliberately self-contained (does not extend Transposer.Common) so that
 * shared OpenComputers files stay untouched.
 */
class METransposer(val host: tileentity.METransposer) extends prefab.ManagedEnvironment with traits.WorldInventoryAnalytics with traits.WorldTankAnalytics with traits.WorldFluidContainerAnalytics with traits.InventoryTransfer with traits.FluidContainerTransfer with DeviceInfo {
  protected def componentName = "me_transposer"

  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent(componentName).
    withConnector().
    create()

  override def position = BlockPosition(host)

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "ME Transposer",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "TP-ME1"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  override protected def checkSideForAction(args: Arguments, n: Int) = args.checkSideAny(n)

  override def onTransferContents(): Option[String] = {
    if (node.tryChangeBuffer(-Settings.get.meTransposerCost)) {
      ServerPacketSender.sendTransposerActivity(host)
      None
    }
    else Option("not enough energy")
  }

  override def fluidTransferRate(): Int = Settings.get.transposerFluidTransferRate

  // ----------------------------------------------------------------------- //
  // The virtual ME side.

  protected def isMe(args: Arguments, index: Int) =
    (args.isString(index) && args.checkString(index).equalsIgnoreCase("me")) ||
      (args.isInteger(index) && args.checkInteger(index) == ForgeDirection.UNKNOWN.ordinal)

  @Callback(doc = """function():boolean -- Get whether the device is actively connected to an ME network (powered and got a channel).""")
  def isMeConnected(context: Context, args: Arguments): Array[AnyRef] = result(host.getProxy.isActive)

  // ----------------------------------------------------------------------- //
  // Filter parsing for ME requests: either a descriptor table (see
  // AEStackFactory) or a database address plus entry index. The filter sits
  // at argument index 2 (count omitted) or 3 (count given).

  protected def filterIndex(args: Arguments): Int =
    Seq(2, 3).find(i => i < args.count && (args.isTable(i) || args.isString(i))).getOrElse(-1)

  /** Returns the parsed stack (or null) and the argument index following the filter. */
  private def parseItemFilter(args: Arguments, offset: Int, amount: Int): (IAEItemStack, Int) = {
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

  @Callback(doc = """function(sourceSide, sinkSide[, count:number[, sourceSlot:number[, sinkSlot:number]]]):number -- Transfer some items between two inventories. Either side may also be the string "me" (or 6) for the ME network; pulling from ME requires a filter (table or dbAddress:string, dbEntry:number) in place of sourceSlot.""")
  override def transferItem(context: Context, args: Arguments): Array[AnyRef] = {
    val sourceIsMe = isMe(args, 0)
    val sinkIsMe = isMe(args, 1)
    if (sourceIsMe && sinkIsMe) result(Unit, "source and sink cannot both be the ME network")
    else if (!sourceIsMe && !sinkIsMe) super.transferItem(context, args)
    else onTransferContents() match {
      case Some(reason) => result(Unit, reason)
      case _ =>
        if (sourceIsMe) transferItemFromMe(args)
        else transferItemToMe(args)
    }
  }

  @Callback(doc = """function(sourceSide:number, sinkSide:number, sourceSlot:number, sinkSlot:number[, safe:boolean]):boolean -- Swap two inventory slots if and only if both directions succeed. Safe swaps require two non-empty slots. The ME network cannot take part in swaps.""")
  override def swap(context: Context, args: Arguments): Array[AnyRef] = {
    if (isMe(args, 0) || isMe(args, 1)) result(Unit, "cannot swap with the ME network")
    else super.swap(context, args)
  }

  @Callback(doc = """function(sourceSide:number, sinkSide:number[, count:number [, sourceTank:number]]):boolean, number -- Transfer some fluid between two tanks. Returns operation result and filled amount""")
  override def transferFluid(context: Context, args: Arguments): Array[AnyRef] = {
    if (isMe(args, 0) || isMe(args, 1)) result(Unit, "this device cannot transfer fluids to or from the ME network")
    else super.transferFluid(context, args)
  }

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

    try {
      val proxy = host.getProxy
      if (!proxy.isActive) return result(Unit, "no ME network")
      val storage = proxy.getStorage.getItemInventory
      val energy = proxy.getEnergy
      val source = new MachineSource(host)

      // Simulate insertion to figure out how much actually fits, then do a
      // powered extraction from the network and commit the insert. Whatever
      // could not be inserted after all is returned to the network.
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

    try {
      val proxy = host.getProxy
      if (!proxy.isActive) return result(Unit, "no ME network")
      val storage = proxy.getStorage.getItemInventory
      val energy = proxy.getEnergy
      val source = new MachineSource(host)

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

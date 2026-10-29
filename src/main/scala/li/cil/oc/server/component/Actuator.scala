package li.cil.oc.server.component

import java.util

import appeng.api.networking.security.IActionHost
import appeng.api.storage.data.IAEItemStack
import appeng.me.helpers.AENetworkProxy
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.common.tileentity
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.ResultWrapper._
import net.minecraft.item.ItemStack

import scala.collection.convert.WrapAsJava._

// Acts on the single wrench-set output side of its host, the other "side" is always the ME network
// the host is cabled into. Transfers are atomic and unrated, capped only by what the ME network and
// the output-side inventory can actually handle.
object Actuator {

  abstract class Common extends prefab.ManagedEnvironment with DeviceInfo with traits.MENetworkTransfer with traits.GregTechMachineInfo with traits.RemotePayer {
    override val node = api.Network.newNode(this, Visibility.Network).
      withComponent("actuator").
      withConnector().
      create()

    def host: EnvironmentHost with tileentity.traits.OutputSide

    private final lazy val deviceInfo = Map(
      DeviceAttribute.Class -> DeviceClass.Generic,
      DeviceAttribute.Description -> "Actuator",
      DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product -> "AC-1"
    )

    override def getDeviceInfo: util.Map[String, String] = deviceInfo

    protected def onTransferContents(): Option[String] =
      if (payer.tryChangeBuffer(-Settings.get.actuatorCost)) None
      else Option("not enough energy")

    protected def position = BlockPosition(host)

    protected def outputSide = host.outputSide

    protected def outputPos = position.offset(outputSide)

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function():boolean -- Get whether the device is actively connected to an ME network (powered and got a channel).""")
    def isMeConnected(context: Context, args: Arguments): Array[AnyRef] = result(proxy.exists(_.isActive))

    // Same names/split as traits.WorldInventoryAnalytics' getInventorySize(side)/getSlotMaxStackSize(side, slot)
    // (used by Transposer and others), just without the side argument - Actuator only has one output side.

    @Callback(doc = """function():number -- Get the number of slots in the inventory on the output side.""")
    def getInventorySize(context: Context, args: Arguments): Array[AnyRef] = {
      val inventory = InventoryUtils.inventoryAt(outputPos).getOrElse(return result(Unit, "no inventory"))
      result(inventory.getSizeInventory)
    }

    @Callback(doc = """function(slot:number):number -- Get the maximum number of items in the specified slot of the inventory on the output side.""")
    def getSlotMaxStackSize(context: Context, args: Arguments): Array[AnyRef] = {
      val inventory = InventoryUtils.inventoryAt(outputPos).getOrElse(return result(Unit, "no inventory"))
      val slot = args.checkSlot(inventory, 0)
      result(Option(inventory.getStackInSlot(slot)).fold(0)(_.getMaxStackSize))
    }

    @Callback(doc = """function([slot:number]):table -- Get a description of the stack in the given slot of the inventory on the output side, or of every slot (1-indexed) if none given.""")
    def getInventoryContent(context: Context, args: Arguments): Array[AnyRef] = {
      val inventory = InventoryUtils.inventoryAt(outputPos).getOrElse(return result(Unit, "no inventory"))
      if (args.count() > 0) result(inventory.getStackInSlot(args.checkSlot(inventory, 0)))
      else {
        val stacks = new Array[ItemStack](inventory.getSizeInventory)
        for (i <- 0 until inventory.getSizeInventory) stacks(i) = inventory.getStackInSlot(i)
        result(stacks)
      }
    }

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function():table -- Scan whatever's on the output side: name, position, and for GregTech machines also its meta tile ID, energy/activity/progress, circuit configuration, and (multiblocks only) efficiency/pollution/maintenance/owner/runtime if applicable.""")
    def scanMachine(context: Context, args: Arguments): Array[AnyRef] = scanMachineAt(host.world, outputPos)

    // Doesn't fit scanMachine()'s read-only shape, but the ghost circuit needs a way to be set.
    @Callback(doc = """function(config:number):boolean -- Set the circuit configuration of the GregTech machine on the output side. Use -1 to remove the circuit.""")
    def setCircuitConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val mte = gtMachine(host.world, outputPos).flatMap(circuitConfigurable).getOrElse(return result(Unit, "machine does not support circuit configuration"))
      setCircuitConfiguration(mte, args.checkInteger(0))
    }

    // ----------------------------------------------------------------------- //
    // Item transfers: always between the ME network and whatever is on the output side.

    @Callback(doc = """function(filter:table[, count:number[, slot:number]]):number -- Export items from the ME network into whatever is on the output side.""")
    def exportItem(context: Context, args: Arguments): Array[AnyRef] = {
      onTransferContents() match {
        case Some(reason) => return result(Unit, reason)
        case _ =>
      }

      val count = args.optItemCount(1)
      val request = Option(AEStackFactory.parse[IAEItemStack](args.checkTable(0))).map { s =>
        s.setStackSize(count)
        s
      }.orNull
      if (request == null) return result(Unit, "invalid filter")

      val inventory = InventoryUtils.inventoryAt(outputPos).getOrElse(return result(Unit, "no inventory"))
      exportItemInto(request, count, inventory, outputSide.getOpposite, args.optSlot(inventory, 2, -1))
    }

    @Callback(doc = """function([count:number[, slot:number]]):number -- Import items from whatever is on the output side into the ME network.""")
    def importItem(context: Context, args: Arguments): Array[AnyRef] = {
      onTransferContents() match {
        case Some(reason) => return result(Unit, reason)
        case _ =>
      }

      val count = args.optItemCount(0)
      val inventory = InventoryUtils.inventoryAt(outputPos).getOrElse(return result(Unit, "no inventory"))
      importItemFrom(inventory, outputSide.getOpposite, args.optSlot(inventory, 1, -1), count)
    }
  }

  /** Hosted by the Actuator block's own tile entity. */
  class Block(val host: tileentity.Actuator) extends Common {
    override protected def proxy = Some(host.getProxy)

    override protected def actionHost: IActionHost = host
  }

  /** Hosted as a microcontroller upgrade; the microcontroller owns the grid node. */
  class Upgrade(val host: tileentity.Microcontroller) extends Common {
    node.setVisibility(Visibility.Neighbors)

    override protected def proxy = Option(host.getProxy)

    override protected def actionHost: IActionHost = host
  }
}

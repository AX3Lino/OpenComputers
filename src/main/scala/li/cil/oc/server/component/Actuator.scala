package li.cil.oc.server.component

import java.util

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.networking.security.IActionHost
import appeng.api.networking.security.MachineSource
import appeng.api.storage.data.IAEItemStack
import appeng.me.GridAccessException
import appeng.me.helpers.AENetworkProxy
import appeng.util.Platform
import gregtech.api.interfaces.IConfigurationCircuitSupport
import gregtech.api.interfaces.metatileentity.IMetaTileEntity
import gregtech.api.metatileentity.BaseMetaTileEntity
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase
import gregtech.api.util.GTUtility
import gregtech.common.items.ItemIntegratedCircuit
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.common.tileentity
import li.cil.oc.integration.Mods
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.ResultWrapper._
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack

import scala.collection.convert.WrapAsJava._

// A single-sided, wrench-rotatable device: one facing side touches a physical block, the other
// "side" is always the ME network it's cabled into. Transfers are atomic and unrated, capped only
// by what the ME network and the facing inventory can actually handle.
object Actuator {

  abstract class Common extends prefab.ManagedEnvironment with DeviceInfo {
    override val node = api.Network.newNode(this, Visibility.Network).
      withComponent("actuator").
      withConnector().
      create()

    def host: tileentity.Actuator

    protected def proxy: Option[AENetworkProxy]

    protected def actionHost: IActionHost

    private final lazy val deviceInfo = Map(
      DeviceAttribute.Class -> DeviceClass.Generic,
      DeviceAttribute.Description -> "Actuator",
      DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product -> "AC-1"
    )

    override def getDeviceInfo: util.Map[String, String] = deviceInfo

    protected def onTransferContents(): Option[String] =
      if (node.tryChangeBuffer(-Settings.get.actuatorCost)) None
      else Option("not enough energy")

    protected def position = BlockPosition(host)

    protected def facingSide = host.facing

    protected def facingPos = position.offset(facingSide)

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function():boolean -- Get whether the device is actively connected to an ME network (powered and got a channel).""")
    def isMeConnected(context: Context, args: Arguments): Array[AnyRef] = result(proxy.exists(_.isActive))

    // Same names/split as traits.WorldInventoryAnalytics' getInventorySize(side)/getSlotMaxStackSize(side, slot)
    // (used by Transposer and others), just without the side argument - Actuator only has one facing side.

    @Callback(doc = """function():number -- Get the number of slots in the inventory on the facing side.""")
    def getInventorySize(context: Context, args: Arguments): Array[AnyRef] = {
      val inventory = InventoryUtils.inventoryAt(facingPos).getOrElse(return result(Unit, "no inventory"))
      result(inventory.getSizeInventory)
    }

    @Callback(doc = """function(slot:number):number -- Get the maximum number of items in the specified slot of the inventory on the facing side.""")
    def getSlotMaxStackSize(context: Context, args: Arguments): Array[AnyRef] = {
      val inventory = InventoryUtils.inventoryAt(facingPos).getOrElse(return result(Unit, "no inventory"))
      val slot = args.checkSlot(inventory, 0)
      result(Option(inventory.getStackInSlot(slot)).fold(0)(_.getMaxStackSize))
    }

    @Callback(doc = """function([slot:number]):table -- Get a description of the stack in the given slot of the inventory on the facing side, or of every slot (1-indexed) if none given.""")
    def getInventoryContent(context: Context, args: Arguments): Array[AnyRef] = {
      val inventory = InventoryUtils.inventoryAt(facingPos).getOrElse(return result(Unit, "no inventory"))
      if (args.count() > 0) result(inventory.getStackInSlot(args.checkSlot(inventory, 0)))
      else {
        val stacks = new Array[ItemStack](inventory.getSizeInventory)
        for (i <- 0 until inventory.getSizeInventory) stacks(i) = inventory.getStackInSlot(i)
        result(stacks)
      }
    }

    // ----------------------------------------------------------------------- //
    // Further info about whatever's on the facing side, as one snapshot table (geolyzer.analyze() idiom).

    private def gtTileEntity(): Option[BaseMetaTileEntity] =
      if (!Mods.GregTech.isAvailable) None
      else host.world.getTileEntity(facingPos.x, facingPos.y, facingPos.z) match {
        case mte: BaseMetaTileEntity => Some(mte)
        case _ => None
      }

    private def gtMachine(): Option[IMetaTileEntity] = gtTileEntity().map(_.getMetaTileEntity)

    private def circuitConfigurableMachine(): Option[IMetaTileEntity with IConfigurationCircuitSupport] =
      gtMachine().collect { case ccs: IMetaTileEntity with IConfigurationCircuitSupport => ccs }

    @Callback(doc = """function():table -- Scan whatever's on the facing side: name, position, and for GregTech machines also its meta tile ID, energy/activity/progress, circuit configuration, and (multiblocks only) efficiency/pollution/maintenance/owner/runtime if applicable.""")
    def scanMachine(context: Context, args: Arguments): Array[AnyRef] = {
      val pos = facingPos
      val block = host.world.getBlock(pos.x, pos.y, pos.z)
      if (block == Blocks.air) return result(Unit, "no block")
      val metadata = host.world.getBlockMetadata(pos.x, pos.y, pos.z)

      val info = new util.HashMap[String, AnyRef]()
      info.put("name", gtMachine() match {
        case Some(mte) => mte.getLocalName
        case None => new ItemStack(block, 1, metadata).getDisplayName
      })
      info.put("x", Int.box(pos.x))
      info.put("y", Int.box(pos.y))
      info.put("z", Int.box(pos.z))
      info.put("metadata", Int.box(metadata))

      gtTileEntity().foreach { gte =>
        info.put("metaTileId", Int.box(gte.getMetaTileID))
        info.put("owner", gte.getOwnerName)
        info.put("isActive", Boolean.box(gte.isActive))
        info.put("isWorkAllowed", Boolean.box(gte.isAllowedToWork))
        info.put("progress", Int.box(gte.getProgress))
        info.put("maxProgress", Int.box(gte.getMaxProgress))
        info.put("energyStored", Long.box(gte.getStoredEU))
        info.put("energyCapacity", Long.box(gte.getEUCapacity))
      }

      // Multiblock-only concepts (pollution, maintenance, parallel efficiency, recipe count, total
      // runtime) - same fields GregTech's own portable scanner (getInfoData) reports, mirrored here
      // so a computer sees the same numbers a player would get from scanning the machine directly.
      gtMachine().foreach {
        case mte: MTEMultiBlockBase =>
          info.put("energyUsage", Int.box(mte.mEUt))
          info.put("maxInputVoltage", Long.box(mte.getMaxInputVoltage))
          info.put("efficiency", Double.box(mte.mEfficiency / 100.0))
          info.put("problems", Int.box(mte.getIdealStatus - mte.getRepairStatus))
          info.put("pollution", Int.box(mte.getAveragePollutionPercentage))
          info.put("recipesDone", Long.box(mte.recipesDone))
          info.put("totalRunTime", Long.box(mte.getTotalRuntimeInTicks))
        case _ =>
      }

      circuitConfigurableMachine().foreach { mte =>
        val slot = mte.getCircuitSlot
        if (slot >= 0 && slot < mte.getSizeInventory) {
          val config = mte.getStackInSlot(slot) match {
            case stack: ItemStack if stack.getItem.isInstanceOf[ItemIntegratedCircuit] => stack.getItemDamage
            case _ => -1
          }
          info.put("circuitConfiguration", Int.box(config))
        }
      }

      result(info)
    }

    // Doesn't fit scanMachine()'s read-only shape, but the ghost circuit needs a way to be set.
    @Callback(doc = """function(config:number):boolean -- Set the circuit configuration of the GregTech machine on the facing side. Use -1 to remove the circuit.""")
    def setCircuitConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val mte = circuitConfigurableMachine().getOrElse(return result(Unit, "machine does not support circuit configuration"))
      val slot = mte.getCircuitSlot
      if (slot < 0 || slot >= mte.getSizeInventory) return result(Unit, "invalid circuit slot")

      val config = args.checkInteger(0)
      if (config == -1) {
        mte.setInventorySlotContents(slot, null)
        result(true)
      }
      else if (config >= 1 && config <= 24) {
        mte.setInventorySlotContents(slot, GTUtility.getIntegratedCircuit(config))
        result(true)
      }
      else result(Unit, s"invalid circuit configuration value: $config, must be 1-24 or -1")
    }

    // ----------------------------------------------------------------------- //
    // Item transfers: always between the ME network and whatever is on the facing side.

    @Callback(doc = """function(filter:table[, count:number[, slot:number]]):number -- Export items from the ME network into whatever is on the facing side.""")
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

      val inventory = InventoryUtils.inventoryAt(facingPos).getOrElse(return result(Unit, "no inventory"))
      val sinkSlot = args.optSlot(inventory, 2, -1)

      val p = proxy.getOrElse(return result(Unit, "no ME network"))
      try {
        if (!p.isActive) return result(Unit, "no ME network")
        val storage = p.getStorage.getItemInventory
        val energy = p.getEnergy
        val source = new MachineSource(actionHost)

        // Simulate insertion first, then extract from the network and commit; any leftover goes back to the network.
        val simulated = request.getItemStack
        val fits =
          if (sinkSlot < 0) InventoryUtils.insertIntoInventory(simulated, inventory, Option(facingSide.getOpposite), count, simulate = true)
          else InventoryUtils.insertIntoInventorySlot(simulated, inventory, Option(facingSide.getOpposite), sinkSlot, count, simulate = true)
        if (!fits) return result(0)

        request.setStackSize(count - simulated.stackSize)
        val extracted = Platform.poweredExtraction(energy, storage, request, source)
        if (extracted == null || extracted.getStackSize == 0) return result(0)

        val stack = extracted.getItemStack
        var moved = stack.stackSize
        if (sinkSlot < 0) InventoryUtils.insertIntoInventory(stack, inventory, Option(facingSide.getOpposite))
        else InventoryUtils.insertIntoInventorySlot(stack, inventory, Option(facingSide.getOpposite), sinkSlot)
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

    @Callback(doc = """function([count:number[, slot:number]]):number -- Import items from whatever is on the facing side into the ME network.""")
    def importItem(context: Context, args: Arguments): Array[AnyRef] = {
      onTransferContents() match {
        case Some(reason) => return result(Unit, reason)
        case _ =>
      }

      val count = args.optItemCount(0)
      val inventory = InventoryUtils.inventoryAt(facingPos).getOrElse(return result(Unit, "no inventory"))
      val sourceSlot = args.optSlot(inventory, 1, -1)

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
          if (sourceSlot < 0) InventoryUtils.extractAnyFromInventory(consumer, inventory, facingSide.getOpposite, count)
          else InventoryUtils.extractFromInventorySlot(consumer, inventory, facingSide.getOpposite, sourceSlot, count)
        result(moved)
      }
      catch {
        case _: GridAccessException => result(Unit, "no ME network")
      }
    }
  }

  /** Hosted by the Actuator block's own tile entity. */
  class Block(val host: tileentity.Actuator) extends Common {
    override protected def proxy = Some(host.getProxy)

    override protected def actionHost: IActionHost = host
  }
}

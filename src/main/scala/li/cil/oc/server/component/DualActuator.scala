package li.cil.oc.server.component

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.networking.security.IActionHost
import appeng.api.networking.security.MachineSource
import appeng.api.storage.data.IAEFluidStack
import appeng.me.GridAccessException
import appeng.util.Platform
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.ResultWrapper._
import net.minecraftforge.fluids.FluidContainerRegistry
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.FluidTankInfo

import scala.collection.convert.WrapAsJava._
import scala.collection.convert.WrapAsScala._

// A DualActuator adds fluid import/export on top of Actuator's item capability, same facing side.
object DualActuator {

  abstract class Common extends Actuator.Common {
    override val node = li.cil.oc.api.Network.newNode(this, Visibility.Network).
      withComponent("dual_actuator").
      withConnector().
      create()

    private final lazy val dualDeviceInfo = Map(
      DeviceAttribute.Class -> DeviceClass.Generic,
      DeviceAttribute.Description -> "Dual Actuator",
      DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product -> "AC-2"
    )

    override def getDeviceInfo: java.util.Map[String, String] = dualDeviceInfo

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function([filter:table]):table -- Get a list of the stored fluids in this device's own ME network.""")
    def getFluidsInNetwork(context: Context, args: Arguments): Array[AnyRef] = {
      val p = proxy.getOrElse(return result(Unit, "no ME network"))
      if (!p.isActive) return result(Unit, "no ME network")
      val filter = networkFilter(args, 0)
      result(p.getStorage.getFluidInventory.getStorageList.view.map(convert).filter(matches(_, filter)).toArray)
    }

    // Tank arguments are 1-indexed, same as item slots - checkTank/optTank below convert to the
    // 0-indexed array position Forge's own FluidTankInfo[]/IFluidHandler API uses internally.

    protected def checkTank(args: Arguments, index: Int, tankCount: Int): Int = {
      val tank = args.checkInteger(index)
      if (tank < 1 || tank > tankCount) throw new IllegalArgumentException("invalid tank index")
      tank - 1
    }

    protected def optTank(args: Arguments, index: Int, tankCount: Int, default: Int): Int =
      if (args.count() > index) checkTank(args, index, tankCount) else default

    @Callback(doc = """function([tank:number]):table -- Get the capacity of the given tank (1-indexed) on the facing side, or of every tank if none given - #result then gives the tank count.""")
    def getTankCapacity(context: Context, args: Arguments): Array[AnyRef] = {
      val handler = FluidUtils.fluidHandlerAt(facingPos).getOrElse(return result(Unit, "no tank"))
      val info = handler.getTankInfo(facingSide.getOpposite)
      if (info == null) return result(Unit, "no tank")
      if (args.count() > 0) result(info(checkTank(args, 0, info.length)).capacity)
      else result(info.map(_.capacity.underlying: AnyRef))
    }

    @Callback(doc = """function([tank:number]):table -- Get the fluid in the given tank (1-indexed) on the facing side, or in every tank if none given.""")
    def getTankContent(context: Context, args: Arguments): Array[AnyRef] = {
      val handler = FluidUtils.fluidHandlerAt(facingPos).getOrElse(return result(Unit, "no tank"))
      val info: Array[FluidTankInfo] = handler.getTankInfo(facingSide.getOpposite)
      if (info == null) return result(Unit, "no tank")
      if (args.count() > 0) result(info(checkTank(args, 0, info.length)))
      else result(info)
    }

    // ----------------------------------------------------------------------- //
    // Fluid transfers: always between the ME network and whatever is on the facing side.

    @Callback(doc = """function(filter:table[, count:number[, tank:number]]):boolean, number -- Export fluid from the ME network into whatever is on the facing side. Returns operation result and filled amount.""")
    def exportFluid(context: Context, args: Arguments): Array[AnyRef] = {
      onTransferContents() match {
        case Some(reason) => return result(Unit, reason)
        case _ =>
      }

      val count = if (args.count > 1) args.optFluidCount(1) else FluidContainerRegistry.BUCKET_VOLUME
      val request = Option(AEStackFactory.parse[IAEFluidStack](args.checkTable(0))).map { s =>
        s.setStackSize(count)
        s
      }.orNull
      if (request == null) return result(Unit, "invalid filter")

      val handler = FluidUtils.fluidHandlerAt(facingPos).getOrElse(return result(Unit, "no tank"))

      val p = proxy.getOrElse(return result(Unit, "no ME network"))
      try {
        if (!p.isActive) return result(Unit, "no ME network")
        val storage = p.getStorage.getFluidInventory
        val energy = p.getEnergy
        val source = new MachineSource(actionHost)

        val simulated = request.getFluidStack

        // Forge's IFluidHandler.fill can't target a specific tank directly - best effort: reject up
        // front if the requested tank already holds a different, incompatible fluid.
        val info = handler.getTankInfo(facingSide.getOpposite)
        val tank = optTank(args, 2, if (info == null) 0 else info.length, -1)
        if (tank >= 0) {
          if (info == null || tank >= info.length) return result(false, 0)
          val existing = info(tank).fluid
          if (existing != null && !existing.isFluidEqual(simulated)) return result(false, 0)
        }

        val fits = handler.fill(facingSide.getOpposite, simulated, false)
        if (fits <= 0) return result(false, 0)

        request.setStackSize(fits)
        val extracted = Platform.poweredExtraction(energy, storage, request, source)
        if (extracted == null || extracted.getStackSize == 0) return result(false, 0)

        val stack = extracted.getFluidStack
        val filled = handler.fill(facingSide.getOpposite, stack, true)
        if (filled < stack.amount) {
          val leftover = extracted.copy()
          leftover.setStackSize(stack.amount - filled)
          storage.injectItems(leftover, Actionable.MODULATE, source)
        }
        result(filled > 0, filled)
      }
      catch {
        case _: GridAccessException => result(Unit, "no ME network")
      }
    }

    @Callback(doc = """function([count:number[, tank:number]]):boolean, number -- Import fluid from whatever is on the facing side into the ME network. Returns operation result and drained amount.""")
    def importFluid(context: Context, args: Arguments): Array[AnyRef] = {
      onTransferContents() match {
        case Some(reason) => return result(Unit, reason)
        case _ =>
      }

      val count = args.optFluidCount(0)
      val handler = FluidUtils.fluidHandlerAt(facingPos).getOrElse(return result(Unit, "no tank"))
      val drainSide = facingSide.getOpposite

      val p = proxy.getOrElse(return result(Unit, "no ME network"))
      try {
        if (!p.isActive) return result(Unit, "no ME network")
        val storage = p.getStorage.getFluidInventory
        val energy = p.getEnergy
        val source = new MachineSource(actionHost)

        val info = if (args.count() > 1) handler.getTankInfo(drainSide) else null
        val sourceTank = optTank(args, 1, if (info == null) 0 else info.length, -1)
        val simulated =
          if (sourceTank < 0) handler.drain(drainSide, count, false)
          else if (info == null || info(sourceTank).fluid == null) null
          else {
            val fluid = info(sourceTank).fluid.copy()
            fluid.amount = count
            handler.drain(drainSide, fluid, false)
          }
        if (simulated == null || simulated.amount <= 0) return result(false, 0)

        val leftover = Platform.poweredInsert(energy, storage, AEApi.instance.storage.createFluidStack(simulated.copy()), source)
        val accepted = simulated.amount - (if (leftover == null) 0 else leftover.getStackSize.toInt)
        if (accepted <= 0) return result(false, 0)

        handler.drain(drainSide, new FluidStack(simulated.getFluid, accepted), true)
        result(true, accepted)
      }
      catch {
        case _: GridAccessException => result(Unit, "no ME network")
      }
    }
  }

  /** Hosted by the DualActuator block's own tile entity. */
  class Block(val host: tileentity.DualActuator) extends Common {
    override protected def proxy = Some(host.getProxy)

    override protected def actionHost: IActionHost = host
  }
}

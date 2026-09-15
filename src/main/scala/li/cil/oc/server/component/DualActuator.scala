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

    @Callback(doc = """function([tank:number]):table -- Get the capacity of the given tank (1-indexed) on the facing side, or of every tank if none given - #result then gives the tank count.""")
    def getTankCapacity(context: Context, args: Arguments): Array[AnyRef] = {
      val handler = FluidUtils.fluidHandlerAt(facingPos).getOrElse(return result(Unit, "no tank"))
      val info = handler.getTankInfo(facingSide.getOpposite)
      if (info == null) return result(Unit, "no tank")
      if (args.count() > 0) result(args.checkTankInfo(handler, facingSide.getOpposite, 0).capacity)
      else result(info.map(_.capacity.underlying: AnyRef))
    }

    @Callback(doc = """function([tank:number]):table -- Get the fluid in the given tank (1-indexed) on the facing side, or in every tank if none given.""")
    def getTankContent(context: Context, args: Arguments): Array[AnyRef] = {
      val handler = FluidUtils.fluidHandlerAt(facingPos).getOrElse(return result(Unit, "no tank"))
      val info: Array[FluidTankInfo] = handler.getTankInfo(facingSide.getOpposite)
      if (info == null) return result(Unit, "no tank")
      if (args.count() > 0) result(args.checkTankInfo(handler, facingSide.getOpposite, 0))
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
        val tank = args.optTankInfo(handler, facingSide.getOpposite, 2, null)
        if (tank != null && tank.fluid != null && !tank.fluid.isFluidEqual(simulated)) return result(false, 0)

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

        val sourceTank = args.optTankInfo(handler, drainSide, 1, null)
        val simulated =
          if (sourceTank == null) handler.drain(drainSide, count, false)
          else if (sourceTank.fluid == null) null
          else {
            val fluid = sourceTank.fluid.copy()
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

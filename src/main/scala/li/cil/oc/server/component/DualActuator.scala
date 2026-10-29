package li.cil.oc.server.component

import appeng.api.networking.security.IActionHost
import appeng.api.storage.data.IAEFluidStack
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
import net.minecraftforge.fluids.FluidTankInfo

import scala.collection.convert.WrapAsJava._

// A DualActuator adds fluid import/export on top of Actuator's item capability, same output side.
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

    @Callback(doc = """function([tank:number]):table -- Get the capacity of the given tank (1-indexed) on the output side, or of every tank if none given - #result then gives the tank count.""")
    def getTankCapacity(context: Context, args: Arguments): Array[AnyRef] = {
      val handler = FluidUtils.fluidHandlerAt(outputPos).getOrElse(return result(Unit, "no tank"))
      val info = handler.getTankInfo(outputSide.getOpposite)
      if (info == null) return result(Unit, "no tank")
      if (args.count() > 0) result(args.checkTankInfo(handler, outputSide.getOpposite, 0).capacity)
      else result(info.map(_.capacity.underlying: AnyRef))
    }

    @Callback(doc = """function([tank:number]):table -- Get the fluid in the given tank (1-indexed) on the output side, or in every tank if none given.""")
    def getTankContent(context: Context, args: Arguments): Array[AnyRef] = {
      val handler = FluidUtils.fluidHandlerAt(outputPos).getOrElse(return result(Unit, "no tank"))
      val info: Array[FluidTankInfo] = handler.getTankInfo(outputSide.getOpposite)
      if (info == null) return result(Unit, "no tank")
      if (args.count() > 0) result(args.checkTankInfo(handler, outputSide.getOpposite, 0))
      else result(info)
    }

    // ----------------------------------------------------------------------- //
    // Fluid transfers: always between the ME network and whatever is on the output side.

    @Callback(doc = """function(filter:table[, count:number[, tank:number]]):boolean, number -- Export fluid from the ME network into whatever is on the output side. Returns operation result and filled amount.""")
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

      val handler = FluidUtils.fluidHandlerAt(outputPos).getOrElse(return result(Unit, "no tank"))
      exportFluidInto(request, handler, outputSide.getOpposite, args.optTankInfo(handler, outputSide.getOpposite, 2, null))
    }

    @Callback(doc = """function([count:number[, tank:number]]):boolean, number -- Import fluid from whatever is on the output side into the ME network. Returns operation result and drained amount.""")
    def importFluid(context: Context, args: Arguments): Array[AnyRef] = {
      onTransferContents() match {
        case Some(reason) => return result(Unit, reason)
        case _ =>
      }

      val count = args.optFluidCount(0)
      val handler = FluidUtils.fluidHandlerAt(outputPos).getOrElse(return result(Unit, "no tank"))
      importFluidFrom(handler, outputSide.getOpposite, args.optTankInfo(handler, outputSide.getOpposite, 1, null), count)
    }
  }

  /** Hosted by the DualActuator block's own tile entity. */
  class Block(val host: tileentity.DualActuator) extends Common {
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

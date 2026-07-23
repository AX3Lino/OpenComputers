package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity
import li.cil.oc.util.ExtendedArguments._
import net.minecraftforge.common.util.ForgeDirection

import scala.collection.convert.WrapAsJava._

// An ME Transposer with an Adapter fused in; block-interfacing behavior comes from externally-registered drivers.
object MEActuator {

  abstract class Common extends METransposer.Common {
    override val node = api.Network.newNode(this, Visibility.Network).
      withComponent("me_actuator").
      withConnector().
      create()

    private final lazy val deviceInfo = Map(
      DeviceAttribute.Class -> DeviceClass.Generic,
      DeviceAttribute.Description -> "ME Actuator",
      DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product -> "AC-ME1"
    )

    override def getDeviceInfo: util.Map[String, String] = deviceInfo

    // ----------------------------------------------------------------------- //
    // Adapter-side correlation.

    protected def connectedAddress(side: ForgeDirection): Option[String]

    @Callback(doc = """function(side:number):string -- Get the network address of whatever's connected on the given side, or nil if none.""")
    def getSideAddress(context: Context, args: Arguments): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      connectedAddress(side) match {
        case Some(address) => result(address)
        case _ => result(Unit, "no component on that side")
      }
    }

    @Callback(doc = """function():table -- Get a table mapping side number to the address of whatever's connected there.""")
    def getConnectedAddresses(context: Context, args: Arguments): Array[AnyRef] = {
      val addresses = ForgeDirection.VALID_DIRECTIONS.flatMap(side => connectedAddress(side).map(side.ordinal.underlying -> _)).toMap
      result(addresses)
    }
  }

  /** Hosted by the ME Actuator block's own tile entity. */
  class Block(val host: tileentity.MEActuator) extends Common with Transposer.BlockHost with METransposer.GridHost {
    override protected def connectedAddress(side: ForgeDirection) = host.connectedAddress(side)
  }

  /** Hosted as a microcontroller build component (Slot.Upgrade). */
  class Upgrade(val host: EnvironmentHost) extends Common with METransposer.GridUpgradeHost with traits.AdapterInterfacing
}

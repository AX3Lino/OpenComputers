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

    // Lets a computer correlate this actuator with whichever driver-backed component ended up on
    // its "remaining" side (e.g. a Computronics gt_machine), since that component's own address
    // carries no positional info and multiple actuators on one network are otherwise indistinguishable.
    @Callback(doc = """function():number, number, number -- Get the X, Y, Z position of this block.""")
    def getCoordinates(context: Context, args: Arguments): Array[AnyRef] = result(position.x, position.y, position.z)
  }

  /** Hosted by the ME Actuator block's own tile entity. */
  class Block(val host: tileentity.MEActuator) extends Common with Transposer.BlockHost with METransposer.GridHost

  /** Hosted as a microcontroller build component (Slot.Upgrade). */
  class Upgrade(val host: EnvironmentHost) extends Common with METransposer.GridUpgradeHost
}

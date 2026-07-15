package li.cil.oc.server.component

import java.util

import appeng.api.networking.security.IActionHost
import appeng.me.helpers.IGridProxyable
import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.BlockPosition

import scala.collection.convert.WrapAsJava._

// An ME Dual Transposer with an Adapter fused in.
object MEDualActuator {

  abstract class Common extends MEDualTransposer.Common {
    override val node = api.Network.newNode(this, Visibility.Network).
      withComponent("me_dual_actuator").
      withConnector().
      create()

    private final lazy val deviceInfo = Map(
      DeviceAttribute.Class -> DeviceClass.Generic,
      DeviceAttribute.Description -> "ME Dual Actuator",
      DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product -> "AC-ME2"
    )

    override def getDeviceInfo: util.Map[String, String] = deviceInfo
  }

  /** Hosted by the ME Dual Actuator block's own tile entity. */
  class Block(val host: tileentity.MEDualActuator) extends Common {
    override def position = BlockPosition(host)

    override protected def proxy = Some(host.getProxy)

    override protected def actionHost: IActionHost = host

    override def fluidTransferRate(): Int = host.info.fluidTransferRate

    override def onTransferContents(): Option[String] = {
      val result = super.onTransferContents()
      if (result.isEmpty) ServerPacketSender.sendTransposerActivity(host)
      result
    }
  }

  /** Hosted as a microcontroller build component (Slot.Upgrade). */
  class Upgrade(val host: EnvironmentHost) extends Common {
    node.setVisibility(Visibility.Neighbors)

    override def position = BlockPosition(host)

    override protected def proxy = host match {
      case p: IGridProxyable => Some(p.getProxy)
      case _ => None
    }

    override protected def actionHost: IActionHost = host.asInstanceOf[IActionHost]

    override def fluidTransferRate(): Int = upgradeFluidTransferRate(host, Constants.BlockName.MEDualActuator)
  }
}

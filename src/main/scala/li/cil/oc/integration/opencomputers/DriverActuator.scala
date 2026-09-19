package li.cil.oc.integration.opencomputers

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.tileentity.Microcontroller
import li.cil.oc.server.component
import li.cil.oc.Constants
import li.cil.oc.api
import net.minecraft.item.ItemStack

object DriverActuator extends Item with HostAware {
  override def worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    api.Items.get(Constants.BlockName.Actuator),
    api.Items.get(Constants.BlockName.DualActuator))

  private def isDual(stack: ItemStack) = api.Items.get(stack) == api.Items.get(Constants.BlockName.DualActuator)

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment =
    if (host.world != null && host.world.isRemote) null
    else host match {
      case host: EnvironmentHost with Microcontroller =>
        if (isDual(stack)) new component.DualActuator.Upgrade(host)
        else new component.Actuator.Upgrade(host)
      case _ => null
    }

  override def slot(stack: ItemStack): String = Slot.Upgrade

  override def tier(stack: ItemStack): Int = if (isDual(stack)) Tier.Three else Tier.Two

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (!worksWith(stack)) null
      else if (isDual(stack)) classOf[component.DualActuator.Upgrade]
      else classOf[component.Actuator.Upgrade]
  }
}

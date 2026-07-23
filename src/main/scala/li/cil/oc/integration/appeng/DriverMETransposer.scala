package li.cil.oc.integration.appeng

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.integration.opencomputers.Item
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

// Lets the ME Transposer/Actuator blocks be selected as a microcontroller build component too.
object DriverMETransposer extends Item with HostAware {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.BlockName.METransposer),
    api.Items.get(Constants.BlockName.MEActuator))

  private def isActuator(stack: ItemStack) = api.Items.get(stack) == api.Items.get(Constants.BlockName.MEActuator)

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else if (isActuator(stack)) new component.MEActuator.Upgrade(host)
    else new component.METransposer.Upgrade(host)

  override def slot(stack: ItemStack) = Slot.Upgrade

  // Balances against a 2-card Transposer+status-reader combo, which already needs a Tier 2 microcontroller case.
  override def tier(stack: ItemStack) =
    if (isActuator(stack)) Tier.Three
    else Tier.Two

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (!worksWith(stack)) null
      else if (isActuator(stack)) classOf[component.MEActuator.Upgrade]
      else classOf[component.METransposer.Upgrade]
  }
}

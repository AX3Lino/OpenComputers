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

// Lets the ME (Dual) Transposer/Actuator blocks be selected as a microcontroller build component too.
object DriverMETransposer extends Item with HostAware {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.BlockName.METransposer),
    api.Items.get(Constants.BlockName.MEDualTransposer),
    api.Items.get(Constants.BlockName.MEActuator),
    api.Items.get(Constants.BlockName.MEDualActuator))

  private def isDualActuator(stack: ItemStack) = api.Items.get(stack) == api.Items.get(Constants.BlockName.MEDualActuator)

  private def isActuator(stack: ItemStack) = api.Items.get(stack) == api.Items.get(Constants.BlockName.MEActuator)

  private def isDual(stack: ItemStack) = api.Items.get(stack) == api.Items.get(Constants.BlockName.MEDualTransposer)

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else if (isDualActuator(stack)) new component.MEDualActuator.Upgrade(host)
    else if (isActuator(stack)) new component.MEActuator.Upgrade(host)
    else if (isDual(stack)) new component.MEDualTransposer.Upgrade(host)
    else new component.METransposer.Upgrade(host)

  override def slot(stack: ItemStack) = Slot.Upgrade

  // Balances against a 2-card Transposer+status-reader combo, which already needs a Tier 2 microcontroller case.
  override def tier(stack: ItemStack) =
    if (isDualActuator(stack) || isActuator(stack)) Tier.Three
    else Tier.Two

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (!worksWith(stack)) null
      else if (isDualActuator(stack)) classOf[component.MEDualActuator.Upgrade]
      else if (isActuator(stack)) classOf[component.MEActuator.Upgrade]
      else if (isDual(stack)) classOf[component.MEDualTransposer.Upgrade]
      else classOf[component.METransposer.Upgrade]
  }
}

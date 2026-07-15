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

/**
 * Lets the ME Transposer, ME Dual Transposer, ME Actuator, and ME Dual
 * Actuator blocks also be selected as a microcontroller build component (in
 * place of a plain Transposer), giving that microcontroller the matching
 * Upgrade card. See common/tileentity/Microcontroller.scala for the AE2 grid
 * connection those cards draw a channel from.
 */
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

  // Component tier for microcontroller/robot assembly (see
  // common/template/MicrocontrollerTemplate.scala): plain Transposer is
  // Tier.One (unchanged, driver default). ME (Dual) Transposer's own AE2
  // network access bumps it to Tier.Two; ME (Dual) Actuator's added GT5
  // machine control bumps it further to Tier.Three - matching how a Tier 1
  // microcontroller case has exactly one upgrade slot (Tier.Two ceiling), so
  // any two-card combo (e.g. Transposer + Geolyzer) already needs a Tier 2
  // case regardless, same floor a single Actuator now requires alone.
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

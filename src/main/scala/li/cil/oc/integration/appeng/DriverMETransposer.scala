package li.cil.oc.integration.appeng

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.integration.opencomputers.Item
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

/**
 * Lets the ME Transposer and ME Dual Transposer blocks also be selected as a
 * microcontroller build component (in place of a plain Transposer), giving
 * that microcontroller an ME Transposer.Upgrade / MEDualTransposer.Upgrade
 * card. See common/tileentity/Microcontroller.scala for the AE2 grid
 * connection those cards draw a channel from.
 */
object DriverMETransposer extends Item with HostAware {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.BlockName.METransposer),
    api.Items.get(Constants.BlockName.MEDualTransposer))

  private def isDual(stack: ItemStack) = api.Items.get(stack) == api.Items.get(Constants.BlockName.MEDualTransposer)

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else if (isDual(stack)) new component.MEDualTransposer.Upgrade(host)
    else new component.METransposer.Upgrade(host)

  override def slot(stack: ItemStack) = Slot.Upgrade

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (!worksWith(stack)) null
      else if (isDual(stack)) classOf[component.MEDualTransposer.Upgrade]
      else classOf[component.METransposer.Upgrade]
  }
}

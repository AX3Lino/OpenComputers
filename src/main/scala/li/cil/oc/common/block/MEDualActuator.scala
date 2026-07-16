package li.cil.oc.common.block

import java.text.NumberFormat

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.item.data.TransposerData.FLUID_TRANSFER_RATE
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.StatCollector
import net.minecraft.world.World

class MEDualActuator extends MEDualTransposer with traits.AdapterInterfacing {
  override protected def customTextures = Array(
    Some("MEDualActuatorTop"),
    Some("MEDualActuatorTop"),
    Some("MEDualActuatorSide"),
    Some("MEDualActuatorSide"),
    Some("MEDualActuatorSide"),
    Some("MEDualActuatorSide")
  )

  @SideOnly(Side.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    Textures.Actuator.iconOn = iconRegister.registerIcon(Settings.resourceDomain + ":ActuatorOn")
  }

  override def createTileEntity(world: World, metadata: Int) = new tileentity.MEDualActuator()

  override protected def tooltipBody(metadata: Int, stack: ItemStack, player: EntityPlayer, tooltip: java.util.List[String], advanced: Boolean) {
    tooltip.addAll(li.cil.oc.util.Tooltip.get(getClass.getSimpleName))

    val tag = stack.getTagCompound
    val transferRate =
      if (tag != null && tag.hasKey(FLUID_TRANSFER_RATE))
        tag.getInteger(FLUID_TRANSFER_RATE)
      else
        Settings.get.transposerFluidTransferRate

    tooltip.add(StatCollector.translateToLocalFormatted("tile.oc.meDualActuator.tooltip", NumberFormat.getIntegerInstance.format(transferRate)))
  }
}

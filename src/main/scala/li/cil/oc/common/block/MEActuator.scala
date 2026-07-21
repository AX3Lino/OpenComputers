package li.cil.oc.common.block

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.world.World

class MEActuator extends METransposer with traits.AdapterInterfacing {
  override protected def customTextures = Array(
    Some("MEActuatorTop"),
    Some("MEActuatorTop"),
    Some("MEActuatorSide"),
    Some("MEActuatorSide"),
    Some("MEActuatorSide"),
    Some("MEActuatorSide")
  )

  @SideOnly(Side.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    Textures.Actuator.iconOn = iconRegister.registerIcon(Settings.resourceDomain + ":ActuatorOn")
  }

  override def createTileEntity(world: World, metadata: Int) = new tileentity.MEActuator()
}

package li.cil.oc.common.block

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.world.World

class Adapter extends SimpleBlock with traits.GUI with traits.AdapterInterfacing {
  override protected def customTextures = Array(
    Some("AdapterTop"),
    Some("AdapterTop"),
    Some("AdapterSide"),
    Some("AdapterSide"),
    Some("AdapterSide"),
    Some("AdapterSide")
  )

  @SideOnly(Side.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    Textures.Adapter.iconOn = iconRegister.registerIcon(Settings.resourceDomain + ":AdapterOn")
  }

  // ----------------------------------------------------------------------- //

  override def guiType = GuiType.Adapter

  override def hasTileEntity(metadata: Int) = true

  override def createTileEntity(world: World, metadata: Int) = new tileentity.Adapter()
}

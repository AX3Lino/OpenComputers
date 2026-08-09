package li.cil.oc.common.block

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

// Faces one direction only, rotated via GT5's wrench (see tileentity.Actuator's IWrenchable
// implementation); the other "side" is always whatever ME network it's cabled into.
class Actuator extends SimpleBlock {
  // Index 3 (SOUTH) is the default local facing, index 2 (NORTH) is directly opposite it - see
  // traits.Rotatable/SimpleBlock.getIcon, which remaps world-space facing back to these local indices
  // via toLocal, so this is rotation-invariant: whichever world side is actually facing always renders
  // Front, and whichever is directly behind it always renders Back.
  override protected def customTextures = Array(
    Some("ActuatorSide"),
    Some("ActuatorSide"),
    Some("ActuatorBack"),
    Some("ActuatorFront"),
    Some("ActuatorSide"),
    Some("ActuatorSide")
  )

  @SideOnly(Side.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    Textures.Actuator.iconOn = iconRegister.registerIcon(Settings.resourceDomain + ":ActuatorOn")
  }

  override def hasTileEntity(metadata: Int) = true

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection) = false

  // Stashes the clicked side into metadata (same trick vanilla BlockHopper uses); read back
  // immediately below, in createTileEntity, so the tile entity is already correctly facing before
  // it's ever attached to the world - setting it later (e.g. in onBlockPlacedBy) would mean a second,
  // separate network update, which is visible to the placing player as the block turning to face.
  override def onBlockPlaced(world: World, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float, metadata: Int): Int = side

  override def createTileEntity(world: World, metadata: Int) = {
    val actuator = new tileentity.Actuator()
    // Same convention as a vanilla hopper: face the block that was placed on, i.e. the opposite of
    // whichever side was clicked.
    actuator.setFromFacing(ForgeDirection.getOrientation(metadata).getOpposite)
    actuator
  }

  override def onBlockPlacedBy(world: World, x: Int, y: Int, z: Int, player: EntityLivingBase, stack: ItemStack) {
    super.onBlockPlacedBy(world, x, y, z, player, stack)
    if (!world.isRemote) {
      world.getTileEntity(x, y, z) match {
        case actuator: tileentity.Actuator =>
          player match {
            case realPlayer: EntityPlayer => actuator.setOwner(realPlayer)
            case _ =>
          }
        case _ =>
      }
    }
  }

  // Shared by the whole Actuator family: skips super.tooltipBody since there's no rate line to compose with anymore.
  protected def tooltipBodyWithOwnDescription(stack: ItemStack, tooltip: java.util.List[String]): Unit = {
    tooltip.addAll(li.cil.oc.util.Tooltip.get(getClass.getSimpleName))
  }

  override protected def tooltipBody(metadata: Int, stack: ItemStack, player: EntityPlayer, tooltip: java.util.List[String], advanced: Boolean): Unit =
    tooltipBodyWithOwnDescription(stack, tooltip)
}

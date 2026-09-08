package li.cil.oc.common.block

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.block.FlippableIcon
import li.cil.oc.client.renderer.block.ActuatorOrientation
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.IIcon
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

// Faces one direction only, rotated via GT5's wrench (see tileentity.Actuator's IWrenchable
// implementation); the other "side" is always whatever ME network it's cabled into.
class Actuator extends SimpleBlock {
  override protected def customTextures = Array(
    Some("ActuatorSide"),
    Some("ActuatorSide"),
    Some("ActuatorBack"),
    Some("ActuatorFront"),
    Some("ActuatorSide"),
    Some("ActuatorSide")
  )

  // One wrapper per world direction, not per local texture slot - the same icon can land on
  // different world faces depending on facing, each needing its own flip state.
  private val globalIconWrappers = Array.fill(6)(new FlippableIcon(null))

  @SideOnly(Side.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    Textures.Actuator.iconOn = iconRegister.registerIcon(Settings.resourceDomain + ":ActuatorOn")
  }

  // World-placed block: real facing from the tile entity. Front/Back stay unmirrored - only the
  // lateral faces need the arrow to point at facing. See BlockRenderer.scala's Actuator case for
  // the matching uvRotate half of this.
  @SideOnly(Side.CLIENT)
  override def getIcon(world: IBlockAccess, x: Int, y: Int, z: Int, globalSide: ForgeDirection, localSide: ForgeDirection): IIcon = {
    val icon = super.getIcon(world, x, y, z, globalSide, localSide)
    val facing = getFacing(world, x, y, z)
    if (facing == ForgeDirection.UNKNOWN || globalSide == facing || globalSide == facing.getOpposite) icon
    else {
      val wrapper = globalIconWrappers(globalSide.ordinal).wrap(icon)
      wrapper.setFlip(ActuatorOrientation.get(facing, globalSide))
      wrapper
    }
  }

  // Held/inventory render has no tile entity - use the default unrotated facing (SOUTH).
  @SideOnly(Side.CLIENT)
  override def getIcon(side: ForgeDirection, metadata: Int): IIcon = {
    val icon = super.getIcon(side, metadata)
    if (side == ForgeDirection.SOUTH || side == ForgeDirection.NORTH) icon
    else {
      val wrapper = globalIconWrappers(side.ordinal).wrap(icon)
      wrapper.setFlip(ActuatorOrientation.get(ForgeDirection.SOUTH, side))
      wrapper
    }
  }

  override def hasTileEntity(metadata: Int) = true

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection) = false

  // Stashes the clicked side into metadata (BlockHopper trick), read back in createTileEntity so
  // the tile entity already faces correctly before it ever attaches to the world.
  override def onBlockPlaced(world: World, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float, metadata: Int): Int = side

  override def createTileEntity(world: World, metadata: Int) = {
    val actuator = new tileentity.Actuator()
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

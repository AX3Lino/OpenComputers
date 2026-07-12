package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

class METransposer extends SimpleBlock {
  override protected def customTextures = Array(
    Some("METransposerTop"),
    Some("METransposerTop"),
    Some("METransposerSide"),
    Some("METransposerSide"),
    Some("METransposerSide"),
    Some("METransposerSide")
  )

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean = false

  // ----------------------------------------------------------------------- //

  override def hasTileEntity(metadata: Int) = true

  override def createTileEntity(world: World, metadata: Int) = new tileentity.METransposer()

  override def onBlockPlacedBy(world: World, x: Int, y: Int, z: Int, player: EntityLivingBase, stack: ItemStack) {
    super.onBlockPlacedBy(world, x, y, z, player, stack)
    if (!world.isRemote) world.getTileEntity(x, y, z) match {
      case transposer: tileentity.METransposer => player match {
        case realPlayer: EntityPlayer => transposer.setOwner(realPlayer)
        case _ =>
      }
      case _ =>
    }
  }
}

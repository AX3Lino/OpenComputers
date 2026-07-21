package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class METransposer extends Transposer {
  override protected def customTextures = Array(
    Some("METransposerTop"),
    Some("METransposerTop"),
    Some("METransposerSide"),
    Some("METransposerSide"),
    Some("METransposerSide"),
    Some("METransposerSide")
  )

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

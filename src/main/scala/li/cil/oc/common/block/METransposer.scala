package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World

// CustomDrops is inherited unchanged from Transposer since tileentity.METransposer is-a tileentity.Transposer.
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

  // Shared by the whole ME family: skips super.tooltipBody, since Transposer's own version adds a rate line under a
  // different lang key, which would duplicate this one - so the own-class description is re-added manually instead.
  protected def tooltipBodyWithOwnDescription(stack: ItemStack, tooltip: java.util.List[String], langKey: String): Unit = {
    tooltip.addAll(li.cil.oc.util.Tooltip.get(getClass.getSimpleName))
    addTransferRateTooltip(tooltip, stack, langKey)
  }

  override protected def tooltipBody(metadata: Int, stack: ItemStack, player: EntityPlayer, tooltip: java.util.List[String], advanced: Boolean): Unit =
    tooltipBodyWithOwnDescription(stack, tooltip, "tile.oc.meTransposer.tooltip")
}

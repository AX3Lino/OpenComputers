package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.StatCollector
import net.minecraft.world.World

class MEDualTransposer(rate: Int) extends METransposer(rate) {
  override protected def customTextures = Array(
    Some("MEDualTransposerTop"),
    Some("MEDualTransposerTop"),
    Some("MEDualTransposerSide"),
    Some("MEDualTransposerSide"),
    Some("MEDualTransposerSide"),
    Some("MEDualTransposerSide")
  )

  override protected def tooltipBody(metadata: Int, stack: ItemStack, player: EntityPlayer, tooltip: java.util.List[String], advanced: Boolean) {
    tooltip.addAll(li.cil.oc.util.Tooltip.get(getClass.getSimpleName))
    tooltip.add(StatCollector.translateToLocalFormatted("tile.oc.meDualTransposer.tooltip", rate.toString))
  }

  override def createTileEntity(world: World, metadata: Int) = new tileentity.MEDualTransposer(rate)
}

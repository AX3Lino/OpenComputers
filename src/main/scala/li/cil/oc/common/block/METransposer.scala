package li.cil.oc.common.block

import java.text.NumberFormat

import li.cil.oc.Settings
import li.cil.oc.common.item.data.TransposerData.FLUID_TRANSFER_RATE
import li.cil.oc.common.tileentity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.StatCollector
import net.minecraft.world.World

/**
 * Built directly on common.block.Transposer: CustomDrops (doCustomInit/
 * doCustomDrops/getPickBlock, all typed to tileentity.Transposer) is
 * inherited unchanged and works correctly here since tileentity.METransposer
 * is itself a tileentity.Transposer. Only what's genuinely different is
 * overridden - art, tile entity class, tooltip's lang key, and adding the
 * AE2 owner-set on placement.
 */
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

  // Deliberately doesn't call super.tooltipBody: Transposer's own version
  // already adds a rate line (under a different lang key), which would
  // duplicate this one. Goes straight to SimpleBlock's description-only
  // behavior instead (getClass.getSimpleName resolves polymorphically, so
  // this still looks up the right "METransposer" tooltip key).
  override protected def tooltipBody(metadata: Int, stack: ItemStack, player: EntityPlayer, tooltip: java.util.List[String], advanced: Boolean): Unit = {
    tooltip.addAll(li.cil.oc.util.Tooltip.get(getClass.getSimpleName))

    val tag = stack.getTagCompound
    val transferRate =
      if (tag != null && tag.hasKey(FLUID_TRANSFER_RATE))
        tag.getInteger(FLUID_TRANSFER_RATE)
      else
        Settings.get.transposerFluidTransferRate

    tooltip.add(StatCollector.translateToLocalFormatted("tile.oc.meTransposer.tooltip", NumberFormat.getIntegerInstance.format(transferRate)))
  }
}

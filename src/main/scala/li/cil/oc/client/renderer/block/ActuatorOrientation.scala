package li.cil.oc.client.renderer.block

import net.minecraft.util.IIcon
import net.minecraftforge.common.util.ForgeDirection

// Implements IIcon directly rather than delegating to AE2's icon-wrapper classes, which call @SideOnly(CLIENT) methods unsafe to run on a dedicated server.
final class FlippableIcon(private var original: IIcon) extends IIcon {
  private var flipU = false
  private var flipV = false

  def wrap(icon: IIcon): FlippableIcon = {
    original = icon
    this
  }

  // Sets the flip bits (8 = horizontal, 16 = vertical) and returns the uvRotate quadrant (low 3 bits).
  def setFlip(orientation: Int): Int = {
    flipU = (orientation & 8) == 8
    flipV = (orientation & 16) == 16
    orientation & 7
  }

  override def getIconWidth: Int = original.getIconWidth
  override def getIconHeight: Int = original.getIconHeight
  override def getMinU: Float = if (flipU) original.getMaxU else original.getMinU
  override def getMaxU: Float = if (flipU) original.getMinU else original.getMaxU
  override def getInterpolatedU(px: Double): Float = if (flipU) original.getInterpolatedU(16 - px) else original.getInterpolatedU(px)
  override def getMinV: Float = if (flipV) original.getMaxV else original.getMinV
  override def getMaxV: Float = if (flipV) original.getMinV else original.getMaxV
  override def getInterpolatedV(px: Double): Float = if (flipV) original.getInterpolatedV(16 - px) else original.getInterpolatedV(px)
  override def getIconName: String = original.getIconName
}

// Per (facing, face) rotation and flip for the arrow texture, derived from AE2's BaseBlockRender
// ORIENTATION_MAP (private there, hence a copy). Low 3 bits = uvRotate quadrant, bit 3 =
// horizontal icon flip, bit 4 = vertical icon flip. +180 degrees is baked in for the current
// placeholder arrow texture (AE2's); re-derive the table if that texture is replaced.
object ActuatorOrientation {
  private val table = Array(
    // facing = DOWN
    Array(11, 11, 0, 0, 0, 0),
    // facing = UP
    Array(3, 3, 3, 3, 3, 3),
    // facing = NORTH
    Array(11, 3, 9, 2, 1, 10),
    // facing = SOUTH
    Array(8, 0, 10, 1, 2, 9),
    // facing = WEST
    Array(10, 1, 10, 1, 1, 10),
    // facing = EAST
    Array(9, 2, 9, 2, 18, 2)
  )

  def get(facing: ForgeDirection, face: ForgeDirection): Int = table(facing.ordinal)(face.ordinal)
}

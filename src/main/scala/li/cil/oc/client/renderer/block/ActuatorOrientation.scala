package li.cil.oc.client.renderer.block

import net.minecraft.util.IIcon
import net.minecraftforge.common.util.ForgeDirection

// Ported from AE2's appeng.client.texture.FlippableIcon: mirrors an icon's U/V axis, since vanilla's
// uvRotate can only rotate in 90 degree steps and never produce a true mirror.
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

// Ported from AE2's appeng.client.render.BaseBlockRender ORIENTATION_MAP (sliced to the single-axis
// forward/up pairs AE2 derives from one tracked facing, see TileInterface.setSide), plus a baked-in
// +180 since the borrowed arrow art points opposite of AE2's own default. Packed value per
// (facing, face): low 3 bits = uvRotate quadrant, bit 3 = horizontal icon flip, bit 4 = vertical.
//
// Two gotchas when touching a row: `get` indexes by facing.getOpposite(), so a row's `// facing = X`
// comment names the row that's actually used when the real facing is X.getOpposite(). And uvRotate
// quadrants aren't numbered sequentially by degree - rot0/rot3 are the true 180-opposite pair (not
// rot0/rot2), so a 180 flip is `3 - rot`, not `(rot + 2) & 3`.
object ActuatorOrientation {
  private val table = Array(
    // facing = DOWN
    Array(3, 3, 3, 3, 3, 3),
    // facing = UP
    Array(11, 11, 0, 0, 0, 0),
    // facing = NORTH
    Array(8, 0, 10, 1, 2, 9),
    // facing = SOUTH
    Array(11, 3, 9, 2, 1, 10),
    // facing = WEST
    Array(9, 2, 9, 2, 18, 2),
    // facing = EAST
    Array(10, 1, 10, 1, 1, 10)
  )

  // AE2's tracked value ("pointAt") is the BACK of their Interface, not the front the way Actuator's
  // own "facing" is - translate here so every caller can keep passing Actuator's real facing.
  def get(facing: ForgeDirection, face: ForgeDirection): Int = table(facing.getOpposite.ordinal)(face.ordinal)
}

package li.cil.oc.client.renderer.block

import net.minecraftforge.common.util.ForgeDirection

// Ported from AE2's appeng.client.render.BaseBlockRender ORIENTATION_MAP (sliced to the single-axis
// forward/up pairs AE2 derives from one tracked facing, see TileInterface.setSide), plus a baked-in
// +180 since the borrowed arrow art points opposite of AE2's own default. Packed value per
// (facing, face): low 3 bits = uvRotate quadrant, bit 3 = horizontal icon flip, bit 4 = vertical.
// ORIENTATION_MAP itself is private to AE2's class, so this table can't just reference it directly -
// icon flipping is handled via AE2's own public appeng.client.texture.TmpFlippableIcon instead.
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

  // AE2's TileInterface.setSide sets pointAt = (clicked face).getOpposite() - so pointAt is one
  // getOpposite() away from the direction Actuator's own "facing" represents. Translate here so
  // every caller can keep passing Actuator's real facing.
  def get(facing: ForgeDirection, face: ForgeDirection): Int = table(facing.getOpposite.ordinal)(face.ordinal)
}

package li.cil.oc.client.renderer.block

import net.minecraft.util.IIcon
import net.minecraftforge.common.util.ForgeDirection

// Ported from AE2's appeng.client.texture.FlippableIcon - a plain IIcon wrapper that can mirror the
// wrapped icon's U and/or V axis independently of vanilla's own uvRotate mechanism (which can only
// rotate in 90 degree steps, never produce a true mirror - see ActuatorOrientation for why both are
// needed together). Reused across renders via `wrap`, one instance per world direction (never per
// local texture slot - the same underlying icon can need different flip states on different faces).
final class FlippableIcon(private var original: IIcon) extends IIcon {
  private var flipU = false
  private var flipV = false

  def wrap(icon: IIcon): FlippableIcon = {
    original = icon
    this
  }

  // Applies the flip bits (8 = horizontal, 16 = vertical) as a side effect and returns the vanilla
  // uvRotate quadrant (low 3 bits) - same dual-purpose call AE2's own render code uses, so the icon
  // flip and the renderer's rotate field always come from one consistent lookup.
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

// Ported from AE2's appeng.client.render.BaseBlockRender ORIENTATION_MAP, sliced down to just the
// (forward, up) pairs AE2 itself uses for a plain single-axis rotation with no independent "up"/roll
// (up = facing.getOpposite(); forward = SOUTH if facing is horizontal, else UP - see AE2's
// TileInterface.setSide), since Actuator has one tracked facing value only. The underlying vertex/UV
// math is vanilla, not AE2-specific, so these values are directly reusable for any block using
// RenderBlocks.renderStandardBlock with a directional per-face texture - confirmed against
// RenderBlocks' own source for the Down/Up case earlier in this project; ported rather than
// re-derived for the remaining four lateral faces to avoid repeating that same derivation by hand.
//
// Packed value per (facing, face): low 3 bits = uvRotate quadrant (0-3), bit 3 = horizontal icon
// flip, bit 4 = vertical icon flip. Indexed [facing.ordinal][face.ordinal], both in vanilla
// ForgeDirection ordinal order (DOWN, UP, NORTH, SOUTH, WEST, EAST).
//
// IMPORTANT when editing a row below: each row's `// facing = X` comment names AE2's *original* row
// (before the getOpposite() translation in `get` below was added), NOT which actual Actuator facing
// selects that row - `get(facing, face)` indexes by `facing.getOpposite()`, so the row commented
// NORTH is the one actually used when facing=SOUTH, and vice versa (same for WEST/EAST). Mixing
// this up once already caused a real regression (2026-09-05) - always re-check `get`'s getOpposite
// call before touching a specific row/column, don't rely on the comment label alone.
object ActuatorOrientation {
  // D-face (world-DOWN acting as a lateral) entries in the NORTH/WEST rows corrected 2026-09-05 from
  // in-game empirical data (AE2's ported values were wrong here specifically): cross-validating the
  // two test rounds against each other (facing=X after the getOpposite fix queries the same row
  // facing=X.getOpposite() did before it) showed D was correct for facing=N/W but exactly opposite
  // (180 degrees) for facing=S/E. D-face confirmed fully correct for all 4 horizontal facings after
  // this fix (re-tested 2026-09-05).
  //
  // N/S/E/W-as-lateral-face entries (the W/E cells in the NORTH/SOUTH rows, N/S cells in the
  // WEST/EAST rows) corrected the same day via a geometric rule, not yet re-verified in-game: every
  // vertical face's rot0=baseline(points UP)/rot3=180(points DOWN) was already confirmed by this
  // point, universal across all 6 lateral faces. Assumed rot1/rot2 are a consistent 90-degree
  // clockwise/counterclockwise turn from baseline **as viewed from outside that specific face** -
  // this is exactly the same kind of view-dependent handedness reversal already confirmed for
  // Down/Up (viewed from below vs above swaps which way "clockwise" points, hence their 1<->2 swap)
  // - applied face by face (e.g. viewed from outside the East face, i.e. looking West, clockwise
  // from UP lands on NORTH, giving E-face rot1=N). Chained the four faces' derived rules into one
  // cycle (E->N->W->S->E) and confirmed it's a single consistent counterclockwise-viewed-from-above
  // rotation - internally coherent, but this is reasoned from a geometric rule, not confirmed
  // against RenderBlocks' actual vertex assignments the way Down/Up was - treat as provisional until
  // re-tested in-game.
  // flipH added to the rot1/rot2 N/S/E/W-lateral entries above 2026-09-05: direction (which edge
  // the line is on) was already confirmed correct for all of these, but the F glyph itself read
  // backwards on E-face (both its rot1 and rot2 uses) and N-face (both its uses), and on D-face
  // specifically for its rot2/rot3 uses (its rot0/rot1 uses already had flipH and were fine). Since
  // flipH only mirrors left-right content and never moves a full-width horizontal stripe's edge
  // (confirmed earlier), adding it here shouldn't disturb the already-correct line direction.
  //
  // Every rotate value below already has +180 degrees baked in on top of what was empirically
  // confirmed correct against the debug glyph: the real arrow art (borrowed as-is from AE2, not
  // edited) has a baked-in default direction 180 degrees opposite of the debug glyph's, so the
  // correction belongs in the data, not as a runtime patch in `get` - flip bits (8/16) are unchanged
  // from what was confirmed.
  //
  // IMPORTANT: vanilla's uvRotate quadrant values are NOT numbered sequentially by degree. rot0 and
  // rot3 are the confirmed (both by reading RenderBlocks.java and by repeated in-game testing this
  // session) 180-degree-opposite pair for every lateral face - NOT rot0/rot2. So the low-2-bit 180
  // flip here is `3 - rot`, swapping 0<->3 and 1<->2; a naive `(rot + 2) & 3` (treating 0/2 and 1/3
  // as the opposite pairs) is wrong and was deployed once, breaking every facing in an inconsistent,
  // not merely-backwards way. If re-deriving any entry from scratch against a debug texture again,
  // remember the final table value here is `3 - (debug-confirmed rotate quadrant)`, not the
  // debug-confirmed value itself.
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

  // facing.getOpposite(): AE2's own tracked value (their "pointAt") turned out to correspond to the
  // BACK of the block, not the front/business side the way Actuator's "facing" does - confirmed
  // empirically (in-game test showed U/D-axis lines exactly opposite of expected, and the two
  // horizontal laterals landing on the wrong axis entirely, both consistent with this specific
  // front/back mismatch, not a broken table). Centralized here so every caller can keep passing
  // Actuator's own real facing without needing to remember the translation.
  def get(facing: ForgeDirection, face: ForgeDirection): Int = table(facing.getOpposite.ordinal)(face.ordinal)
}

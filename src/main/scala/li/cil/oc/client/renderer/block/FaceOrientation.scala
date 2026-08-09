package li.cil.oc.client.renderer.block

import net.minecraftforge.common.util.ForgeDirection

// Per-face UV rotation needed to keep the Actuator's directional ("Side") texture pointing toward
// `forward` on every face, not just the one it's actually facing. Derived empirically (not ported
// from elsewhere), then verified against a full facing/rotation matrix across all six orientations.
//
// Each axis-opposite pair of faces (Down/Up, North/South, East/West) renders from an identical UV
// formula, yet - the same as vanilla's well-known Y+/Y- quirk - needs a mirrored (1-and-2-swapped)
// rotation table to produce the same reading: Down and Up need swapped values to both read "toward
// forward", and so do North/South and East/West. Only one member of each pair is "primary" below;
// its partner is the same table with cases 1 and 2 exchanged.
object FaceOrientation {
  def get(face: ForgeDirection, forward: ForgeDirection): Int = face match {
    case ForgeDirection.DOWN => forward match {
      case ForgeDirection.SOUTH => 0
      case ForgeDirection.EAST => 1
      case ForgeDirection.WEST => 2
      case ForgeDirection.NORTH => 3
      case _ => 0
    }
    case ForgeDirection.UP => forward match {
      case ForgeDirection.SOUTH => 0
      case ForgeDirection.WEST => 1
      case ForgeDirection.EAST => 2
      case ForgeDirection.NORTH => 3
      case _ => 0
    }
    case ForgeDirection.NORTH => forward match {
      case ForgeDirection.DOWN => 0
      case ForgeDirection.EAST => 1
      case ForgeDirection.WEST => 2
      case ForgeDirection.UP => 3
      case _ => 0
    }
    case ForgeDirection.SOUTH => forward match {
      case ForgeDirection.DOWN => 0
      case ForgeDirection.WEST => 1
      case ForgeDirection.EAST => 2
      case ForgeDirection.UP => 3
      case _ => 0
    }
    case ForgeDirection.EAST => forward match {
      case ForgeDirection.DOWN => 0
      case ForgeDirection.SOUTH => 1
      case ForgeDirection.NORTH => 2
      case ForgeDirection.UP => 3
      case _ => 0
    }
    case ForgeDirection.WEST => forward match {
      case ForgeDirection.DOWN => 0
      case ForgeDirection.NORTH => 1
      case ForgeDirection.SOUTH => 2
      case ForgeDirection.UP => 3
      case _ => 0
    }
    case _ => 0
  }
}

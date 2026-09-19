package li.cil.oc.common.tileentity.traits

import net.minecraftforge.common.util.ForgeDirection

// The side a block acts on.
trait OutputSide extends Rotatable {
  def outputSide: ForgeDirection
}

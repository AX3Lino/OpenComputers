package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.world.World

class MEDualTransposer extends METransposer {
  override protected def customTextures = Array(
    Some("MEDualTransposerTop"),
    Some("MEDualTransposerTop"),
    Some("MEDualTransposerSide"),
    Some("MEDualTransposerSide"),
    Some("MEDualTransposerSide"),
    Some("MEDualTransposerSide")
  )

  override def createTileEntity(world: World, metadata: Int) = new tileentity.MEDualTransposer()
}

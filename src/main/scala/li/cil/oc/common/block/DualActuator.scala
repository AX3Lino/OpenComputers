package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

class DualActuator extends Actuator {
  override protected def customTextures = Array(
    Some("DualActuatorSide"),
    Some("DualActuatorSide"),
    Some("DualActuatorBack"),
    Some("DualActuatorFront"),
    Some("DualActuatorSide"),
    Some("DualActuatorSide")
  )

  override def createTileEntity(world: World, metadata: Int) = {
    val actuator = new tileentity.DualActuator()
    actuator.setFromFacing(ForgeDirection.getOrientation(metadata).getOpposite)
    actuator
  }

  override protected def tooltipBody(metadata: Int, stack: ItemStack, player: EntityPlayer, tooltip: java.util.List[String], advanced: Boolean): Unit =
    tooltipBodyWithOwnDescription(stack, tooltip)
}

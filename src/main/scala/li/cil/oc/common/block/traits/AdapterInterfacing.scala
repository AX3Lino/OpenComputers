package li.cil.oc.common.block.traits

import li.cil.oc.common.block.SimpleBlock
import li.cil.oc.common.tileentity
import li.cil.oc.integration.util.Wrench
import li.cil.oc.util.BlockPosition
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

// Adapter-style neighbor-driver rescan + wrench side toggle, shared by Adapter and the ME Actuator family.
trait AdapterInterfacing extends SimpleBlock {
  override def onNeighborBlockChange(world: World, x: Int, y: Int, z: Int, block: Block) =
    world.getTileEntity(x, y, z) match {
      case host: tileentity.traits.AdapterInterfacing => host.neighborChanged()
      case _ => // Ignore.
    }

  override def onNeighborChange(world: IBlockAccess, x: Int, y: Int, z: Int, tileX: Int, tileY: Int, tileZ: Int) =
    world.getTileEntity(x, y, z) match {
      case host: tileentity.traits.AdapterInterfacing =>
        val (dx, dy, dz) = (tileX - x, tileY - y, tileZ - z)
        val index = 3 + dx + dy + dy + dz + dz + dz
        if (index >= 0 && index < AdapterInterfacing.sides.length) {
          host.neighborChanged(AdapterInterfacing.sides(index))
        }
      case _ => // Ignore.
    }

  override def onBlockActivated(world: World, x: Int, y: Int, z: Int, player: EntityPlayer, side: ForgeDirection, hitX: Float, hitY: Float, hitZ: Float) = {
    if (Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))) {
      val sideToToggle = if (player.isSneaking) side.getOpposite else side
      world.getTileEntity(x, y, z) match {
        case host: tileentity.traits.AdapterInterfacing =>
          if (!world.isRemote) {
            val oldValue = host.openSides(sideToToggle.ordinal())
            host.setSideOpen(sideToToggle, !oldValue)
          }
          true
        case _ => false
      }
    }
    else super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ)
  }
}

object AdapterInterfacing {
  private val sides = Array(
    ForgeDirection.NORTH,
    ForgeDirection.DOWN,
    ForgeDirection.WEST,
    ForgeDirection.UNKNOWN,
    ForgeDirection.EAST,
    ForgeDirection.UP,
    ForgeDirection.SOUTH)
}

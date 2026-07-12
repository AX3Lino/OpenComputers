package li.cil.oc.integration.appeng

import java.util

import appeng.api.implementations.IPowerChannelState
import appeng.core.localization.WailaText
import li.cil.oc.common.block
import li.cil.oc.common.tileentity
import mcp.mobius.waila.api.IWailaConfigHandler
import mcp.mobius.waila.api.IWailaDataAccessor
import mcp.mobius.waila.api.IWailaDataProvider
import mcp.mobius.waila.api.IWailaRegistrar
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.World

/**
 * Shows the same "Device Online/Offline/Missing Channel" line AE2's own
 * Waila provider shows for its tiles, on both ME Transposer variants.
 * Registered only when both AE2 and Waila are present (see ModWaila), so
 * referencing AE2 classes is safe.
 */
object WailaMETransposer extends IWailaDataProvider {
  def init(registrar: IWailaRegistrar) {
    registrar.registerBodyProvider(this, classOf[block.METransposer])
    registrar.registerNBTProvider(this, classOf[tileentity.METransposer])
  }

  override def getNBTData(player: EntityPlayerMP, tileEntity: TileEntity, tag: NBTTagCompound, world: World, x: Int, y: Int, z: Int) = {
    tileEntity match {
      case te: IPowerChannelState =>
        tag.setBoolean("aeActive", te.isActive)
        tag.setBoolean("aePowered", te.isPowered)
      case _ =>
    }
    tag
  }

  override def getWailaBody(stack: ItemStack, tooltip: util.List[String], accessor: IWailaDataAccessor, config: IWailaConfigHandler): util.List[String] = {
    val tag = accessor.getNBTData
    if (tag != null && tag.hasKey("aeActive")) {
      tooltip.add(WailaText.getPowerState(tag.getBoolean("aeActive"), tag.getBoolean("aePowered"), false))
    }
    tooltip
  }

  override def getWailaStack(accessor: IWailaDataAccessor, config: IWailaConfigHandler) = accessor.getStack

  override def getWailaHead(stack: ItemStack, tooltip: util.List[String], accessor: IWailaDataAccessor, config: IWailaConfigHandler) = tooltip

  override def getWailaTail(stack: ItemStack, tooltip: util.List[String], accessor: IWailaDataAccessor, config: IWailaConfigHandler) = tooltip
}

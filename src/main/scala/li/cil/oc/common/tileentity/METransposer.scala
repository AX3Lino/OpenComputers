package li.cil.oc.common.tileentity

import java.util

import appeng.api.implementations.IPowerChannelState
import appeng.api.networking.GridFlags
import appeng.api.networking.IGridNode
import appeng.api.networking.security.IActionHost
import appeng.api.util.AECableType
import appeng.api.util.DimensionalCoord
import appeng.me.helpers.AENetworkProxy
import appeng.me.helpers.IGridProxyable
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.EventHandler
import li.cil.oc.server.component
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.ForgeDirection

/**
 * A Transposer with an added AE2 grid connection for its virtual "me" side.
 * Extends tileentity.Transposer directly - `info: TransposerData` (the
 * fluid transfer rate, NBT-persisted exactly like vanilla's own fluid
 * regulator boost) and `node`/its NBT read-write plumbing all come from
 * there unchanged; only the `transposer` component field is overridden
 * (item transfer now also understands the "me" side) and the AE2 grid proxy
 * itself is new.
 */
class METransposer extends Transposer with IGridProxyable with IActionHost with IPowerChannelState {
  protected def blockName = Constants.BlockName.METransposer

  override val transposer: component.Transposer.Common = new component.METransposer.Block(this)

  // ----------------------------------------------------------------------- //
  // AE2 grid node.

  private lazy val gridProxy = {
    val proxy = new AENetworkProxy(this, "proxy", api.Items.get(blockName).createItemStack(1), true)
    proxy.setFlags(GridFlags.REQUIRE_CHANNEL)
    proxy.setIdlePowerUsage(Settings.get.meTransposerIdleAEPower)
    proxy.setValidSides(util.EnumSet.complementOf(util.EnumSet.of(ForgeDirection.UNKNOWN)))
    proxy
  }

  override def getProxy: AENetworkProxy = gridProxy

  override def getGridNode(dir: ForgeDirection): IGridNode = gridProxy.getNode

  override def getActionableNode: IGridNode = gridProxy.getNode

  override def getCableConnectionType(dir: ForgeDirection): AECableType = AECableType.SMART

  override def getLocation = new DimensionalCoord(this)

  override def gridChanged() {}

  override def securityBreak() {
    world.func_147480_a(x, y, z, true)
  }

  def setOwner(player: EntityPlayer): Unit = gridProxy.setOwner(player)

  // IPowerChannelState, consumed by the Waila provider in integration.appeng.
  override def isActive = gridProxy.isActive

  override def isPowered = gridProxy.isPowered

  // ----------------------------------------------------------------------- //

  override protected def initialize() {
    super.initialize()
    if (isServer) {
      EventHandler.scheduleServer(() => if (!isInvalid && !gridProxy.isReady) gridProxy.onReady())
    }
  }

  override def dispose() {
    super.dispose()
    if (isServer) {
      gridProxy.invalidate()
    }
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: NBTTagCompound) {
    super.readFromNBTForServer(nbt)
    gridProxy.readFromNBT(nbt)
  }

  override def writeToNBTForServer(nbt: NBTTagCompound) {
    super.writeToNBTForServer(nbt)
    gridProxy.writeToNBT(nbt)
  }
}

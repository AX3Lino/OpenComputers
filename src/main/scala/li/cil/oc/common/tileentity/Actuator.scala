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
import ic2.api.tile.IWrenchable
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.EventHandler
import li.cil.oc.server.component
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.ForgeDirection

// Single wrench-rotatable facing side (traits.Rotatable). Also implements IC2's IWrenchable, purely so
// GT5's wrench (BehaviourWrench/BlockOverlayRenderer) recognizes this as directly-facing-settable and
// draws its rotation grid + current-facing indicator, the same as it does for hoppers/droppers/etc.
// AE2's IOrientable was tried first, but its front+up dual-axis model doesn't match a block that only
// ever has one meaningful direction - GTUtility.determineWrenchingSide's clicked-zone direction should
// become the new facing directly, not rotate an axis relative to the current one.
class Actuator extends traits.Environment with traits.Rotatable with IWrenchable with IGridProxyable with IActionHost with IPowerChannelState {
  protected def blockName = Constants.BlockName.Actuator

  override def wrenchCanSetFacing(player: EntityPlayer, side: Int): Boolean = true

  override def getFacing: Short = facing.ordinal.toShort

  override def setFacing(side: Short): Unit = setFromFacing(ForgeDirection.getOrientation(side))

  override def wrenchCanRemove(player: EntityPlayer): Boolean = false

  override def getWrenchDropRate: Float = 1.0f

  override def getWrenchDrop(player: EntityPlayer): ItemStack = null

  val actuator: component.Actuator.Common = new component.Actuator.Block(this)

  def node = actuator.node

  override def canUpdate = false

  // ----------------------------------------------------------------------- //
  // AE2 grid node.

  private lazy val gridProxy = {
    val proxy = new AENetworkProxy(this, "proxy", api.Items.get(blockName).createItemStack(1), true)
    proxy.setFlags(GridFlags.REQUIRE_CHANNEL)
    proxy.setIdlePowerUsage(Settings.get.actuatorIdleAEPower)
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
    actuator.load(nbt)
  }

  override def writeToNBTForServer(nbt: NBTTagCompound) {
    super.writeToNBTForServer(nbt)
    gridProxy.writeToNBT(nbt)
    actuator.save(nbt)
  }
}

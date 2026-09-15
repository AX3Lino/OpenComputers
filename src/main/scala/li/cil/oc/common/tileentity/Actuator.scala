package li.cil.oc.common.tileentity

import java.util

import appeng.api.implementations.IPowerChannelState
import appeng.api.networking.GridFlags
import appeng.api.networking.IGridNode
import appeng.api.networking.events.MENetworkChannelsChanged
import appeng.api.networking.events.MENetworkEventSubscribe
import appeng.api.networking.events.MENetworkPowerStatusChange
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

  // IPowerChannelState. The grid node only exists server-side, so the client renders from a synced copy.
  private var clientActive = false
  private var clientPowered = false

  override def isActive = if (isServer) gridProxy.isActive else clientActive

  override def isPowered = if (isServer) gridProxy.isPowered else clientPowered

  @MENetworkEventSubscribe
  def onPowerStatusChange(event: MENetworkPowerStatusChange): Unit = world.markBlockForUpdate(x, y, z)

  @MENetworkEventSubscribe
  def onChannelsChanged(event: MENetworkChannelsChanged): Unit = world.markBlockForUpdate(x, y, z)

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

  override def readFromNBTForClient(nbt: NBTTagCompound) {
    super.readFromNBTForClient(nbt)
    clientActive = nbt.getBoolean("active")
    clientPowered = nbt.getBoolean("powered")
  }

  override def writeToNBTForClient(nbt: NBTTagCompound) {
    super.writeToNBTForClient(nbt)
    nbt.setBoolean("active", gridProxy.isActive)
    nbt.setBoolean("powered", gridProxy.isPowered)
  }
}

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
 * `rate` is fixed by which block variant this is (see Constants.METransposerRateTiers
 * and common/block/METransposer.scala) - it's only a `var` and NBT-persisted here
 * because Minecraft reloads tile entities via a no-arg constructor + NBT, bypassing
 * the block that originally placed it.
 */
class METransposer(var rate: Int) extends traits.Environment with traits.TransposerActivity with IGridProxyable with IActionHost with IPowerChannelState {
  def this() = this(Constants.METransposerRateTiers.head._2)

  protected def blockName = Constants.BlockName.METransposer

  protected def createComponent: component.METransposer.Common = new component.METransposer.Block(this)

  val metransposer = createComponent

  def node = metransposer.node

  override def canUpdate = false

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
    if (nbt.hasKey(Settings.namespace + "rate")) rate = nbt.getInteger(Settings.namespace + "rate")
    metransposer.load(nbt)
    gridProxy.readFromNBT(nbt)
  }

  override def writeToNBTForServer(nbt: NBTTagCompound) {
    super.writeToNBTForServer(nbt)
    nbt.setInteger(Settings.namespace + "rate", rate)
    metransposer.save(nbt)
    gridProxy.writeToNBT(nbt)
  }
}

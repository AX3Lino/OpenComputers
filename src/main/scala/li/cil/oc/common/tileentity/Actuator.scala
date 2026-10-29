package li.cil.oc.common.tileentity

import ic2.api.tile.IWrenchable
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.server.component
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.ForgeDirection

// Single wrench-rotatable facing side (traits.Rotatable). Also implements IC2's IWrenchable, purely so
// GT5's wrench (BehaviourWrench/BlockOverlayRenderer) recognizes this as directly-facing-settable and
// draws its rotation grid + current-facing indicator, the same as it does for hoppers/droppers/etc.
class Actuator extends traits.Environment with traits.OutputSide with IWrenchable with traits.MENetworkNode {
  protected def blockName = Constants.BlockName.Actuator

  override protected def idleAEPower = Settings.get.actuatorIdleAEPower

  override def outputSide: ForgeDirection = facing

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

  override def readFromNBTForServer(nbt: NBTTagCompound) {
    super.readFromNBTForServer(nbt)
    actuator.load(nbt)
  }

  override def writeToNBTForServer(nbt: NBTTagCompound) {
    super.writeToNBTForServer(nbt)
    actuator.save(nbt)
  }
}

package li.cil.oc.common.tileentity.traits

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import ic2.api.tile.IWrenchable
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.ForgeDirection

// GregTech single-block wrench model via IC2's IWrenchable: a plain wrench sets the output side (any side but the front), sneak + wrench sets the front.
trait SelectableOutputSide extends OutputSide with IWrenchable {
  // UNKNOWN means "not set explicitly", which resolves to the side opposite the front.
  private var _outputSide = ForgeDirection.UNKNOWN

  // IWrenchable.setFacing gets no player; GregTech calls wrenchCanSetFacing right before it.
  private var wrenchSneaking = false

  override def outputSide: ForgeDirection =
    if (_outputSide == ForgeDirection.UNKNOWN || _outputSide == facing) facing.getOpposite
    else _outputSide

  def setOutputSide(side: ForgeDirection): Boolean = {
    if (side == ForgeDirection.UNKNOWN || side == facing || side == outputSide) false
    else {
      _outputSide = side
      onOutputSideChanged()
      true
    }
  }

  def setFrontSide(side: ForgeDirection): Boolean = {
    if (!validFacings.contains(side) || side == facing) false
    else {
      val previousOutput = outputSide
      setFromFacing(side)
      if (previousOutput == side) _outputSide = side.getOpposite
      onOutputSideChanged()
      true
    }
  }

  protected def onOutputSideChanged(): Unit = if (isServer) world.markBlockForUpdate(x, y, z)

  // ----------------------------------------------------------------------- //

  override def wrenchCanSetFacing(player: EntityPlayer, side: Int): Boolean = {
    wrenchSneaking = player.isSneaking
    val direction = ForgeDirection.getOrientation(side)
    if (wrenchSneaking) validFacings.contains(direction) && direction != facing
    else direction != facing && direction != outputSide
  }

  // GT's wrench overlay highlights getFacing without sneak awareness, so the client reports whichever side a wrench would set right now.
  override def getFacing: Short = (if (isClient && clientSneaking) facing else outputSide).ordinal.toShort

  @SideOnly(Side.CLIENT)
  private def clientSneaking = {
    val player = Minecraft.getMinecraft.thePlayer
    player != null && player.isSneaking
  }

  override def setFacing(side: Short): Unit = {
    val direction = ForgeDirection.getOrientation(side)
    if (wrenchSneaking) setFrontSide(direction) else setOutputSide(direction)
  }

  override def wrenchCanRemove(player: EntityPlayer): Boolean = false

  override def getWrenchDropRate: Float = 1.0f

  override def getWrenchDrop(player: EntityPlayer): ItemStack = null

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: NBTTagCompound) = {
    super.readFromNBTForServer(nbt)
    if (nbt.hasKey(Settings.namespace + "outputSide")) {
      _outputSide = ForgeDirection.getOrientation(nbt.getInteger(Settings.namespace + "outputSide"))
    }
  }

  override def writeToNBTForServer(nbt: NBTTagCompound) = {
    super.writeToNBTForServer(nbt)
    nbt.setInteger(Settings.namespace + "outputSide", _outputSide.ordinal)
  }

  @SideOnly(Side.CLIENT)
  override def readFromNBTForClient(nbt: NBTTagCompound) {
    super.readFromNBTForClient(nbt)
    _outputSide = ForgeDirection.getOrientation(nbt.getInteger("outputSide"))
  }

  override def writeToNBTForClient(nbt: NBTTagCompound) {
    super.writeToNBTForClient(nbt)
    nbt.setInteger("outputSide", _outputSide.ordinal)
  }
}

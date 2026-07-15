package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.common.item.data.TransposerData
import li.cil.oc.server.component
import li.cil.oc.util.ExtendedNBT.extendNBTTagCompound
import net.minecraft.nbt.NBTTagCompound

class Transposer extends traits.Environment with traits.TransposerActivity {
  val info = new TransposerData()

  // Widened to the Common supertype (rather than the inferred concrete
  // Block type) so ME Transposer and friends can override this field with
  // their own component while still extending this tile entity directly -
  // see common/tileentity/METransposer.scala.
  val transposer: component.Transposer.Common = new component.Transposer.Block(this)

  def node = transposer.node

  override def canUpdate = false

  override def readFromNBTForServer(nbt: NBTTagCompound) {
    super.readFromNBTForServer(nbt)
    info.load(nbt.getCompoundTag(Settings.namespace + "info"))
    transposer.load(nbt)
  }

  override def writeToNBTForServer(nbt: NBTTagCompound) {
    super.writeToNBTForServer(nbt)
    nbt.setNewCompoundTag(Settings.namespace + "info", info.save)
    transposer.save(nbt)
  }
}

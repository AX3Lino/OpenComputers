package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.server.component

class MEDualTransposer(rate: Int) extends METransposer(rate) {
  def this() = this(Constants.METransposerRateTiers.head._2)

  override protected def blockName = Constants.BlockName.MEDualTransposer

  override protected def createComponent = new component.MEDualTransposer.Block(this)
}

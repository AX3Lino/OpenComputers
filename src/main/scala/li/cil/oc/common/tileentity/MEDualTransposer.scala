package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.server.component

class MEDualTransposer extends METransposer {
  override protected def blockName = Constants.BlockName.MEDualTransposer

  override val transposer: component.Transposer.Common = new component.MEDualTransposer.Block(this)
}

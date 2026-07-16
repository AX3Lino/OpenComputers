package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.server.component

class MEDualActuator extends MEDualTransposer with traits.AdapterInterfacing {
  override protected def blockName = Constants.BlockName.MEDualActuator

  override val transposer: component.Transposer.Common = new component.MEDualActuator.Block(this)

  override protected def defaultState = true
}

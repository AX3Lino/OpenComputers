package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.server.component

class MEActuator extends METransposer with traits.AdapterInterfacing {
  override protected def blockName = Constants.BlockName.MEActuator

  override val transposer: component.Transposer.Common = new component.MEActuator.Block(this)

  override protected def defaultState = true
}

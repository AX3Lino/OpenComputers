package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.server.component

class DualActuator extends Actuator {
  override protected def blockName = Constants.BlockName.DualActuator

  override val actuator: component.Actuator.Common = new component.DualActuator.Block(this)
}

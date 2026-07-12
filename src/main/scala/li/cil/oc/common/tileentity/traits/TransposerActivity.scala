package li.cil.oc.common.tileentity.traits

trait TransposerActivity extends TileEntity {
  // Used on client side to check whether to render activity indicators.
  var lastOperation = 0L
}

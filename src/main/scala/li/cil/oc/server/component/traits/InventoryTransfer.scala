package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component._
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.InventoryUtils

trait InventoryTransfer extends traits.WorldAware with traits.SideRestricted {
  // Return None on success, else Some("failure reason")
  def onTransferContents(): Option[String]

  // Override to let a side argument address a network resource (e.g. an ME network) instead of a physical
  // inventory/tank at a world position - see METransposer/MEDualTransposer for the "me" side implementation.
  protected def isVirtualSide(args: Arguments, index: Int): Boolean = false

  protected def virtualSideBothError: String = "source and sink cannot both be a virtual side"

  // Called instead of the normal item transfer once isVirtualSide confirmed exactly one of source/sink is virtual.
  protected def transferItemVirtual(context: Context, args: Arguments, sourceIsVirtual: Boolean): Array[AnyRef] =
    result(Unit, "invalid side")

  // Called instead of the normal fluid transfer once isVirtualSide confirmed exactly one of source/sink is virtual.
  protected def transferFluidVirtual(context: Context, args: Arguments, sourceIsVirtual: Boolean): Array[AnyRef] =
    result(Unit, "invalid side")

  @Callback(doc = """function(sourceSide:number, sinkSide:number[, count:number[, sourceSlot:number[, sinkSlot:number]]]):number -- Transfer some items between two inventories.""")
  def transferItem(context: Context, args: Arguments): Array[AnyRef] = {
    val sourceIsVirtual = isVirtualSide(args, 0)
    val sinkIsVirtual = isVirtualSide(args, 1)
    if (sourceIsVirtual || sinkIsVirtual) {
      if (sourceIsVirtual && sinkIsVirtual) return result(Unit, virtualSideBothError)
      return onTransferContents() match {
        case Some(reason) => result(Unit, reason)
        case _ => transferItemVirtual(context, args, sourceIsVirtual)
      }
    }

    val sourceSide = checkSideForAction(args, 0)
    val sourcePos = position.offset(sourceSide)
    val sinkSide = checkSideForAction(args, 1)
    val sinkPos = position.offset(sinkSide)
    val count = args.optItemCount(2)

    onTransferContents() match {
      case Some(reason) =>
        result(Unit, reason)
      case _ =>
        val extractor = if (args.count > 3) {
          val sourceSlot = args.checkSlot(InventoryUtils.inventoryAt(sourcePos).getOrElse(throw new IllegalArgumentException("no inventory")), 3)
          val sinkSlot = args.optSlot(InventoryUtils.inventoryAt(sinkPos).getOrElse(throw new IllegalArgumentException("no inventory")), 4, -1)

          InventoryUtils.getTransferBetweenInventoriesSlotsAt(sourcePos, sourceSide.getOpposite, sourceSlot, sinkPos, Option(sinkSide.getOpposite), if (sinkSlot < 0) None else Option(sinkSlot), count)
        }
        else
          InventoryUtils.getTransferBetweenInventoriesAt(sourcePos, sourceSide.getOpposite, sinkPos, Option(sinkSide.getOpposite), count)

        Option(extractor) match {
          case Some(ex) => result(ex())
          case _ => result(Unit, "no inventory")
        }
    }
  }

  @Callback(doc = """function(sourceSide:number, sinkSide:number[, count:number [, sourceTank:number]]):boolean, number -- Transfer some fluid between two tanks. Returns operation result and filled amount""")
  def transferFluid(context: Context, args: Arguments): Array[AnyRef] = {
    val sourceIsVirtual = isVirtualSide(args, 0)
    val sinkIsVirtual = isVirtualSide(args, 1)
    if (sourceIsVirtual || sinkIsVirtual) {
      if (sourceIsVirtual && sinkIsVirtual) return result(Unit, virtualSideBothError)
      return onTransferContents() match {
        case Some(reason) => result(Unit, reason)
        case _ => transferFluidVirtual(context, args, sourceIsVirtual)
      }
    }

    val sourceSide = checkSideForAction(args, 0)
    val sourcePos = position.offset(sourceSide)
    val sinkSide = checkSideForAction(args, 1)
    val sinkPos = position.offset(sinkSide)
    val count = args.optFluidCount(2)
    val sourceTank = args.optInteger(3, -1)

    onTransferContents() match {
      case Some(reason) =>
        result(Unit, reason)
      case _ =>
        val moved = FluidUtils.transferBetweenFluidHandlersAt(sourcePos, sourceSide.getOpposite, sinkPos, sinkSide.getOpposite, count, sourceTank)
        if (moved > 0) context.pause(moved / Settings.get.transposerFluidTransferRate) // Allow up to 16 buckets per second.
        result(moved > 0, moved)
    }
  }
}

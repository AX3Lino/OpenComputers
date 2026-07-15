package li.cil.oc.server.component.traits

import cpw.mods.fml.common.Optional
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.integration.Mods
import li.cil.oc.server.component._
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedWorld._
import net.minecraftforge.common.util.ForgeDirection

/**
 * Lets a component read and control the recipe progress of a single adjacent
 * GregTech machine, mirroring the read/write surface of Computronics' own
 * `gt_machine` Adapter component (integration/gregtech/gregtech5/DriverMachine.java
 * there) but implemented directly against GT5's IMachineProgress.
 *
 * GregTech is optional here (same pattern already used by
 * traits/power/AppliedEnergistics2.scala for AE2): the host block only needs
 * AE2 to register and to move items/fluids over its "me" side, so these
 * public callbacks never reference GT5 types directly in their own bytecode -
 * they check availability first, then delegate to the @Optional.Method-
 * annotated private layer below, which is the only place IMachineProgress is
 * ever named. Mixed into both ME Actuator and ME Dual Actuator.
 */
trait GTMachineStatus extends WorldAware {
  private def gregTechAvailable = Mods.GregTech.isAvailable

  // ----------------------------------------------------------------------- //
  // Public callbacks: no GregTech types in their own signatures/bodies, safe
  // to load regardless of whether GregTech is installed. All take an
  // explicit side, same convention as transferItem/transferFluid, since
  // different sides may face different machines.

  @Callback(doc = """function(side:number):boolean -- Returns whether the GT machine on the specified side currently has work queued.""")
  def hasWork(context: Context, args: Arguments): Array[AnyRef] = {
    if (!gregTechAvailable) return result(Unit, "GregTech not installed")
    gtHasWork(args.checkSideAny(0)) match {
      case Some(value) => result(value)
      case _ => result(Unit, "no machine")
    }
  }

  @Callback(doc = """function(side:number):number -- Returns the current recipe progress of the GT machine on the specified side.""")
  def getWorkProgress(context: Context, args: Arguments): Array[AnyRef] = {
    if (!gregTechAvailable) return result(Unit, "GregTech not installed")
    gtGetWorkProgress(args.checkSideAny(0)) match {
      case Some(value) => result(value)
      case _ => result(Unit, "no machine")
    }
  }

  @Callback(doc = """function(side:number):number -- Returns the max recipe progress of the GT machine on the specified side.""")
  def getWorkMaxProgress(context: Context, args: Arguments): Array[AnyRef] = {
    if (!gregTechAvailable) return result(Unit, "GregTech not installed")
    gtGetWorkMaxProgress(args.checkSideAny(0)) match {
      case Some(value) => result(value)
      case _ => result(Unit, "no machine")
    }
  }

  @Callback(doc = """function(side:number):boolean -- Returns whether the GT machine on the specified side is currently active.""")
  def isMachineActive(context: Context, args: Arguments): Array[AnyRef] = {
    if (!gregTechAvailable) return result(Unit, "GregTech not installed")
    gtIsMachineActive(args.checkSideAny(0)) match {
      case Some(value) => result(value)
      case _ => result(Unit, "no machine")
    }
  }

  @Callback(doc = """function(side:number):boolean -- Returns whether the GT machine on the specified side is currently allowed to work.""")
  def isWorkAllowed(context: Context, args: Arguments): Array[AnyRef] = {
    if (!gregTechAvailable) return result(Unit, "GregTech not installed")
    gtIsWorkAllowed(args.checkSideAny(0)) match {
      case Some(value) => result(value)
      case _ => result(Unit, "no machine")
    }
  }

  @Callback(doc = """function(side:number, allowed:boolean):boolean -- Sets whether the GT machine on the specified side is allowed to work. Returns whether a machine was found.""")
  def setWorkAllowed(context: Context, args: Arguments): Array[AnyRef] = {
    if (!gregTechAvailable) return result(false, "GregTech not installed")
    val side = args.checkSideAny(0)
    val allowed = args.checkBoolean(1)
    result(gtSetWorkAllowed(side, allowed))
  }

  // ----------------------------------------------------------------------- //
  // Private, @Optional.Method-annotated layer: the only place IMachineProgress
  // is ever named. Only ever invoked after gregTechAvailable has already been
  // checked above, so it's never reached when GregTech isn't installed -
  // @Optional.Method is the belt to that suspenders.

  @Optional.Method(modid = Mods.IDs.GregTech)
  private def machineAt(side: ForgeDirection): Option[gregtech.api.interfaces.tileentity.IMachineProgress] = {
    val pos = position.offset(side)
    pos.world.filter(_.blockExists(pos)).flatMap(w => Option(w.getTileEntity(pos))) collect {
      case machine: gregtech.api.interfaces.tileentity.IMachineProgress => machine
    }
  }

  @Optional.Method(modid = Mods.IDs.GregTech)
  private def gtHasWork(side: ForgeDirection): Option[Boolean] = machineAt(side).map(_.hasThingsToDo())

  @Optional.Method(modid = Mods.IDs.GregTech)
  private def gtGetWorkProgress(side: ForgeDirection): Option[Int] = machineAt(side).map(_.getProgress())

  @Optional.Method(modid = Mods.IDs.GregTech)
  private def gtGetWorkMaxProgress(side: ForgeDirection): Option[Int] = machineAt(side).map(_.getMaxProgress())

  @Optional.Method(modid = Mods.IDs.GregTech)
  private def gtIsMachineActive(side: ForgeDirection): Option[Boolean] = machineAt(side).map(_.isActive())

  @Optional.Method(modid = Mods.IDs.GregTech)
  private def gtIsWorkAllowed(side: ForgeDirection): Option[Boolean] = machineAt(side).map(_.isAllowedToWork())

  @Optional.Method(modid = Mods.IDs.GregTech)
  private def gtSetWorkAllowed(side: ForgeDirection, allowed: Boolean): Boolean = machineAt(side) match {
    case Some(machine) =>
      if (allowed) machine.enableWorking() else machine.disableWorking()
      true
    case _ => false
  }
}

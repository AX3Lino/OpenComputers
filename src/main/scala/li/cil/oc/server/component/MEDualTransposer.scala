package li.cil.oc.server.component

import java.util

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.networking.security.MachineSource
import appeng.api.storage.data.IAEFluidStack
import appeng.me.GridAccessException
import appeng.util.Platform
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.common.tileentity
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.FluidUtils
import net.minecraftforge.fluids.FluidContainerRegistry
import net.minecraftforge.fluids.FluidStack

import scala.collection.convert.WrapAsJava._

/**
 * An ME Transposer that can also move fluids in and out of the ME network.
 */
class MEDualTransposer(host: tileentity.MEDualTransposer) extends METransposer(host) {
  override protected def componentName = "me_dual_transposer"

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "ME Dual Transposer",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "TP-ME2"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  private def pauseForFluid(context: Context, moved: Int) {
    val delay = moved.toDouble / fluidTransferRate().toDouble - 0.05
    if (delay > 0) context.pause(delay)
  }

  private def parseFluidFilter(args: Arguments, offset: Int, amount: Int): IAEFluidStack = {
    if (args.isTable(offset)) {
      Option(AEStackFactory.parse[IAEFluidStack](args.checkTable(offset))).map { s =>
        s.setStackSize(amount)
        s
      }.orNull
    }
    else {
      Option(DatabaseAccess.getStackFromDatabase(node, args, offset)).flatMap { s =>
        Option(FluidContainerRegistry.getFluidForFilledItem(s))
      }.map { fluid =>
        fluid.amount = amount
        AEApi.instance.storage.createFluidStack(fluid)
      }.orNull
    }
  }

  // ----------------------------------------------------------------------- //
  // Fluid transfers.

  @Callback(doc = """function(sourceSide, sinkSide[, count:number[, sourceTank:number]]):boolean, number -- Transfer some fluid between two tanks. Either side may also be the string "me" (or 6) for the ME network; pulling from ME requires a filter (table or dbAddress:string, dbEntry:number). Returns operation result and filled amount""")
  override def transferFluid(context: Context, args: Arguments): Array[AnyRef] = {
    val sourceIsMe = isMe(args, 0)
    val sinkIsMe = isMe(args, 1)
    if (sourceIsMe && sinkIsMe) result(Unit, "source and sink cannot both be the ME network")
    else if (!sourceIsMe && !sinkIsMe) super.transferFluid(context, args)
    else onTransferContents() match {
      case Some(reason) => result(Unit, reason)
      case _ =>
        if (sourceIsMe) transferFluidFromMe(context, args)
        else transferFluidToMe(context, args)
    }
  }

  private def transferFluidFromMe(context: Context, args: Arguments): Array[AnyRef] = {
    val sinkSide = checkSideForAction(args, 1)
    val sinkPos = position.offset(sinkSide)
    val filterAt = filterIndex(args)
    if (filterAt < 0) return result(Unit, "filter required when pulling from the ME network")
    val count = if (filterAt > 2) args.optFluidCount(2) else FluidContainerRegistry.BUCKET_VOLUME

    val request = parseFluidFilter(args, filterAt, count)
    if (request == null) return result(Unit, "invalid filter")

    val handler = FluidUtils.fluidHandlerAt(sinkPos).getOrElse(return result(Unit, "no tank"))

    try {
      val proxy = host.getProxy
      if (!proxy.isActive) return result(Unit, "no ME network")
      val storage = proxy.getStorage.getFluidInventory
      val energy = proxy.getEnergy
      val source = new MachineSource(host)

      val simulated = request.getFluidStack
      val fits = handler.fill(sinkSide.getOpposite, simulated, false)
      if (fits <= 0) return result(false, 0)

      request.setStackSize(fits)
      val extracted = Platform.poweredExtraction(energy, storage, request, source)
      if (extracted == null || extracted.getStackSize == 0) return result(false, 0)

      val stack = extracted.getFluidStack
      val filled = handler.fill(sinkSide.getOpposite, stack, true)
      if (filled < stack.amount) {
        val leftover = extracted.copy()
        leftover.setStackSize(stack.amount - filled)
        storage.injectItems(leftover, Actionable.MODULATE, source)
      }
      pauseForFluid(context, filled)
      result(filled > 0, filled)
    }
    catch {
      case _: GridAccessException => result(Unit, "no ME network")
    }
  }

  private def transferFluidToMe(context: Context, args: Arguments): Array[AnyRef] = {
    val sourceSide = checkSideForAction(args, 0)
    val sourcePos = position.offset(sourceSide)
    val count = args.optFluidCount(2)
    val sourceTank = args.optInteger(3, -1)
    val handler = FluidUtils.fluidHandlerAt(sourcePos).getOrElse(return result(Unit, "no tank"))
    val drainSide = sourceSide.getOpposite

    try {
      val proxy = host.getProxy
      if (!proxy.isActive) return result(Unit, "no ME network")
      val storage = proxy.getStorage.getFluidInventory
      val energy = proxy.getEnergy
      val source = new MachineSource(host)

      val simulated =
        if (sourceTank < 0) handler.drain(drainSide, count, false)
        else {
          val info = handler.getTankInfo(drainSide)
          if (info == null || sourceTank >= info.length || info(sourceTank).fluid == null) null
          else {
            val fluid = info(sourceTank).fluid.copy()
            fluid.amount = count
            handler.drain(drainSide, fluid, false)
          }
        }
      if (simulated == null || simulated.amount <= 0) return result(false, 0)

      val leftover = Platform.poweredInsert(energy, storage, AEApi.instance.storage.createFluidStack(simulated.copy()), source)
      val accepted = simulated.amount - (if (leftover == null) 0 else leftover.getStackSize.toInt)
      if (accepted <= 0) return result(false, 0)

      handler.drain(drainSide, new FluidStack(simulated.getFluid, accepted), true)
      pauseForFluid(context, accepted)
      result(true, accepted)
    }
    catch {
      case _: GridAccessException => result(Unit, "no ME network")
    }
  }
}

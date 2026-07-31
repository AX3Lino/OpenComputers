package li.cil.oc.integration.ae2fc

import appeng.api.parts.IPartHost
import appeng.api.storage.data.IAEFluidStack
import com.glodblock.github.common.parts.PartFluidInterface
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ResultWrapper._
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

// Part-form counterpart to DriverBlockFluidInterface, for the cable-mounted ME Fluid Interface.
object DriverPartFluidInterface extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).filter(_ != null).exists(_.isInstanceOf[PartFluidInterface])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = new Environment(world.getTileEntity(x, y, z).asInstanceOf[IPartHost])

  final class Environment(val host: IPartHost) extends ManagedTileEntityEnvironment[IPartHost](host, "fluid_interface") with NamedBlock {
    override def preferredName = "fluid_interface"

    // Negative so it never outranks the item-interface driver's priority (0, see DriverPartInterface.scala),
    // keeping the merged component resolved to "me_interface" - same reasoning as DriverBlockFluidInterface.
    override def priority = -1

    private def getPart(side: ForgeDirection): PartFluidInterface = host.getPart(side) match {
      case part: PartFluidInterface => part
      case _ => null
    }

    @Callback(doc = "function(side:number, slot:number):table -- Get the fluid configured in the given slot of the interface pointing in the specified direction.")
    def getFluidConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val slot = args.checkInteger(1)
      getPart(side) match {
        case null => result(Unit, "no matching part")
        case part => result(part.getDualityFluid.getConfig.getFluidStackInSlot(slot))
      }
    }

    @Callback(doc = "function(side:number, slot:number[, detail:table]):boolean -- Configure the filter in the fluid interface on the given slot, pointing in the specified direction.")
    def setFluidConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val slot = args.checkInteger(1)
      getPart(side) match {
        case null => result(Unit, "no matching part")
        case part =>
          val stack: IAEFluidStack = if (args.count() <= 2) null
          else part.getDualityFluid.getStandardFluid(AEStackFactory.parseFluid(args.checkTable(2)))
          part.setConfig(slot, stack)
          context.pause(0.5)
          result(true)
      }
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (Ae2FcUtil.isPartFluidInterface(stack))
        classOf[Environment]
      else null
  }
}

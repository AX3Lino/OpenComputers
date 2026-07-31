package li.cil.oc.integration.ae2fc

import appeng.api.storage.data.IAEFluidStack
import com.glodblock.github.common.tile.TileFluidInterface
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ResultWrapper._
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

// AE2FluidCraft's own bundled OC driver only accepts a Database-card lookup for setting a slot's
// filter, not a plain descriptor table like our own ME Interface driver does. Rather than patching
// a third-party mod, this registers alongside it under distinct callback names, backed by the same
// DualityFluidInterface config AE2FluidCraft's own driver reads/writes.
object DriverBlockFluidInterface extends DriverSidedTileEntity {
  def getTileEntityClass: Class[_] = classOf[TileFluidInterface]

  def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): ManagedEnvironment =
    new Environment(world.getTileEntity(x, y, z).asInstanceOf[TileFluidInterface])

  final class Environment(val tile: TileFluidInterface) extends ManagedTileEntityEnvironment[TileFluidInterface](tile, "fluid_interface") with NamedBlock {

    override def preferredName = "fluid_interface"

    // Kept below the item-interface driver's priority (5) so the merged component keeps resolving
    // to "me_interface", matching the address callers were already using before this driver existed.
    override def priority = 1

    @Callback(doc = "function(slot:number):table -- Get the fluid configured in the given slot of the interface.")
    def getFluidConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val slot = args.checkInteger(0)
      result(tile.getDualityFluid.getConfig.getFluidStackInSlot(slot))
    }

    @Callback(doc = "function(slot:number[, detail:table]):boolean -- Configure the filter in the fluid interface on the given slot.")
    def setFluidConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val slot = args.checkInteger(0)
      val stack: IAEFluidStack = if (args.count() <= 1) null
      else tile.getDualityFluid.getStandardFluid(AEStackFactory.parseFluid(args.checkTable(1)))
      tile.setConfig(slot, stack)
      context.pause(0.5)
      result(true)
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (Ae2FcUtil.isFluidInterface(stack))
        classOf[Environment]
      else null
  }
}

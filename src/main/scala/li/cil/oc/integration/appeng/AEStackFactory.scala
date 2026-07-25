package li.cil.oc.integration.appeng

import java.util

import appeng.api.storage.data.{IAEFluidStack, IAEItemStack}
import appeng.util.item.{AEFluidStack, AEItemStack}
import li.cil.oc.integration.util.MapUtils.MapWrapper
import li.cil.oc.integration.vanilla.{ConverterFluidStack, ConverterItemStack}

object AEStackFactory {
  def parseItem(map: util.Map[_, _]): IAEItemStack = {
    val stack = AEItemStack.create(ConverterItemStack.parse(map))
    stack.setStackSize(map.getLong("size").getOrElse(1))
    stack
  }

  def parseFluid(map: util.Map[_, _]): IAEFluidStack = {
    val stack = AEFluidStack.create(ConverterFluidStack.parse(map))
    stack.setStackSize(map.getLong("size").getOrElse(1))
    stack
  }
}

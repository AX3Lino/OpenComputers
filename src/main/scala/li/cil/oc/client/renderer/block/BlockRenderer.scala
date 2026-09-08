package li.cil.oc.client.renderer.block

import com.gtnewhorizons.angelica.api.ThreadSafeISBRH
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler
import li.cil.oc.Settings
import li.cil.oc.client.renderer.tileentity.RobotRenderer
import li.cil.oc.common
import net.minecraft.block.Block
import net.minecraft.client.renderer.RenderBlocks
import net.minecraft.client.renderer.Tessellator
import net.minecraft.util.IIcon
import net.minecraft.world.IBlockAccess
import net.minecraftforge.common.util.ForgeDirection
import org.lwjgl.opengl.GL11

@ThreadSafeISBRH(perThread = false)
object BlockRenderer extends ISimpleBlockRenderingHandler {
  def getRenderId = Settings.blockRenderId

  override def shouldRender3DInInventory(modelID: Int) = true

  override def renderInventoryBlock(block: Block, metadata: Int, modelID: Int, realRenderer: RenderBlocks) {

    val renderer = patchedRenderer(realRenderer, block)
    val tessellator = Tessellator.instance
    GL11.glPushMatrix()
    block match {
      case _: common.block.Assembler =>
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f)
        tessellator.startDrawingQuads()
        Assembler.render(block, metadata, renderer)
        tessellator.draw()

      case _: common.block.Hologram =>
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f)
        tessellator.startDrawingQuads()
        Hologram.render(block, metadata, renderer)
        tessellator.draw()

      case _: common.block.Printer =>
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f)
        tessellator.startDrawingQuads()
        Printer.render(block, metadata, renderer)
        tessellator.draw()

      case _@(_: common.block.RobotProxy | _: common.block.RobotAfterimage) =>
        GL11.glScalef(1.5f, 1.5f, 1.5f)
        GL11.glTranslatef(-0.5f, -0.4f, -0.5f)
        RobotRenderer.renderChassis()

      case _: common.block.NetSplitter =>
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f)
        tessellator.startDrawingQuads()
        NetSplitter.render(block, metadata, renderer)
        tessellator.draw()

      case _: common.block.Transposer =>
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f)
        tessellator.startDrawingQuads()
        Transposer.render(block, metadata, renderer)
        tessellator.draw()

      case _: common.block.Actuator =>
        // Held/inventory render has no tile entity/real facing - use the fixed default (SOUTH),
        // matching common.block.Actuator's own getIcon(side, metadata) override for the icon-flip
        // half of this. uvRotate handles the rotation half; ActuatorOrientation.get packs both, see
        // that object's comment for where the values come from (ported from AE2's ME Interface).
        block match {
          case simple: common.block.SimpleBlock =>
            simple.setBlockBoundsForItemRender(metadata)
            simple.preItemRender(metadata)
          case _ => block.setBlockBoundsForItemRender()
        }
        renderer.setRenderBoundsFromBlock(block)
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f)

        setActuatorUvRotate(renderer, ForgeDirection.SOUTH)

        tessellator.startDrawingQuads()
        renderFaceYNeg(block, metadata, renderer)
        renderFaceYPos(block, metadata, renderer)
        renderFaceZNeg(block, metadata, renderer)
        renderFaceZPos(block, metadata, renderer)
        renderFaceXNeg(block, metadata, renderer)
        renderFaceXPos(block, metadata, renderer)
        tessellator.draw()

        clearUvRotate(renderer)

      case _ =>
        block match {
          case simple: common.block.SimpleBlock =>
            simple.setBlockBoundsForItemRender(metadata)
            simple.preItemRender(metadata)
          case _ => block.setBlockBoundsForItemRender()
        }
        renderer.setRenderBoundsFromBlock(block)
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f)
        tessellator.startDrawingQuads()
        renderFaceYNeg(block, metadata, renderer)
        renderFaceYPos(block, metadata, renderer)
        renderFaceZNeg(block, metadata, renderer)
        renderFaceZPos(block, metadata, renderer)
        renderFaceXNeg(block, metadata, renderer)
        renderFaceXPos(block, metadata, renderer)
        tessellator.draw()

    }
    GL11.glPopMatrix()

  }

  // RenderBlocks' own uvRotateXXX fields are, confusingly, NOT named after the face they affect for
  // the four lateral faces (confirmed directly in its source): renderFaceZNeg (NORTH) reads
  // uvRotateEast, renderFaceZPos (SOUTH) reads uvRotateWest, renderFaceXNeg (WEST) reads
  // uvRotateNorth, renderFaceXPos (EAST) reads uvRotateSouth. Only uvRotateTop/Bottom match their
  // face (UP/DOWN). The assignment below is intentionally crossed to match reality, not the names.
  private def setActuatorUvRotate(renderer: RenderBlocks, forward: ForgeDirection): Unit = {
    renderer.uvRotateBottom = ActuatorOrientation.get(forward, ForgeDirection.DOWN) & 7
    renderer.uvRotateTop = ActuatorOrientation.get(forward, ForgeDirection.UP) & 7
    renderer.uvRotateEast = ActuatorOrientation.get(forward, ForgeDirection.NORTH) & 7
    renderer.uvRotateWest = ActuatorOrientation.get(forward, ForgeDirection.SOUTH) & 7
    renderer.uvRotateNorth = ActuatorOrientation.get(forward, ForgeDirection.WEST) & 7
    renderer.uvRotateSouth = ActuatorOrientation.get(forward, ForgeDirection.EAST) & 7
  }

  private def clearUvRotate(renderer: RenderBlocks): Unit = {
    renderer.uvRotateBottom = 0
    renderer.uvRotateTop = 0
    renderer.uvRotateNorth = 0
    renderer.uvRotateSouth = 0
    renderer.uvRotateWest = 0
    renderer.uvRotateEast = 0
  }

  override def renderWorldBlock(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, modelId: Int, realRenderer: RenderBlocks) = {

    val renderer = patchedRenderer(realRenderer, block)
    world.getTileEntity(x, y, z) match {
      case assembler: common.tileentity.Assembler =>
        Assembler.render(assembler.block, assembler.getBlockMetadata, x, y, z, renderer)

        true
      case _: common.tileentity.Cable =>
        Cable.render(world, x, y, z, block, renderer)

        true
      case hologram: common.tileentity.Hologram =>
        Hologram.render(hologram.block, hologram.getBlockMetadata, x, y, z, renderer)

        true
      case keyboard: common.tileentity.Keyboard =>
        val result = Keyboard.render(keyboard, x, y, z, block, renderer)

        result
      case print: common.tileentity.Print =>
        Print.render(print.data, print.state, print.facing, x, y, z, block, renderer)

        true
      case _: common.tileentity.Printer =>
        Printer.render(block, x, y, z, renderer)

        true
      case rack: common.tileentity.Rack =>
        Rack.render(rack, x, y, z, block.asInstanceOf[common.block.Rack], renderer)

        true
      case splitter: common.tileentity.NetSplitter =>
        NetSplitter.render(ForgeDirection.VALID_DIRECTIONS.map(splitter.isSideOpen), block, x, y, z, renderer)

        true
      case _: common.tileentity.Transposer =>
        Transposer.render(block, x, y, z, renderer)

        true
      case actuator: common.tileentity.Actuator =>
        setActuatorUvRotate(renderer, actuator.facing)
        val result = renderer.renderStandardBlock(block, x, y, z)
        clearUvRotate(renderer)

        result
      case _ =>
        val result = renderer.renderStandardBlock(block, x, y, z)

        result
    }
  }

  private def needsFlipping(block: Block) =
    block.isInstanceOf[common.block.Hologram] ||
      block.isInstanceOf[common.block.Printer] ||
      block.isInstanceOf[common.block.Print] ||
      block.isInstanceOf[common.block.NetSplitter] ||
      block.isInstanceOf[common.block.Transposer]

  val patchedRenderBlocksThreadLocal = new ThreadLocal[PatchedRenderBlocks]() {
    override def initialValue = new PatchedRenderBlocks()
  }

  private def copyState(renderer: RenderBlocks, patched: RenderBlocks): RenderBlocks = {
    patched.blockAccess = renderer.blockAccess
    patched.overrideBlockTexture = renderer.overrideBlockTexture
    patched.flipTexture = renderer.flipTexture
    patched.renderAllFaces = renderer.renderAllFaces
    patched.useInventoryTint = renderer.useInventoryTint
    patched.renderFromInside = renderer.renderFromInside
    patched.renderMinX = renderer.renderMinX
    patched.renderMaxX = renderer.renderMaxX
    patched.renderMinY = renderer.renderMinY
    patched.renderMaxY = renderer.renderMaxY
    patched.renderMinZ = renderer.renderMinZ
    patched.renderMaxZ = renderer.renderMaxZ
    patched.lockBlockBounds = renderer.lockBlockBounds
    patched.partialRenderBounds = renderer.partialRenderBounds
    patched
  }

  // The texture flip this works around only seems to occur for blocks with custom block renderers?
  // NOTE: this cannot fix Actuator/DualActuator's Down-face mirroring - vanilla's renderFaceYNeg has
  // no flipTexture/uvRotate escape hatch capable of undoing a true mirror (confirmed against
  // RenderBlocks' own source); that's handled with a pre-mirrored texture file instead, at the
  // customTextures level in common/block/Actuator.scala, not here.
  def patchedRenderer(renderer: RenderBlocks, block: Block) =
    if (needsFlipping(block)) {
      copyState(renderer, patchedRenderBlocksThreadLocal.get())
    }
    else renderer

  class PatchedRenderBlocks extends RenderBlocks {
    override def renderFaceXPos(block: Block, x: Double, y: Double, z: Double, texture: IIcon) {
      flipTexture = !flipTexture
      super.renderFaceXPos(block, x, y, z, texture)
      flipTexture = !flipTexture
    }

    override def renderFaceZNeg(block: Block, x: Double, y: Double, z: Double, texture: IIcon) {
      flipTexture = !flipTexture
      super.renderFaceZNeg(block, x, y, z, texture)
      flipTexture = !flipTexture
    }
  }

  def renderFaceXPos(block: Block, metadata: Int, renderer: RenderBlocks) {
    Tessellator.instance.setNormal(1, 0, 0)
    renderer.renderFaceXPos(block, 0, 0, 0, renderer.getBlockIconFromSideAndMetadata(block, ForgeDirection.EAST.ordinal, metadata))
  }

  def renderFaceXNeg(block: Block, metadata: Int, renderer: RenderBlocks) {
    Tessellator.instance.setNormal(-1, 0, 0)
    renderer.renderFaceXNeg(block, 0, 0, 0, renderer.getBlockIconFromSideAndMetadata(block, ForgeDirection.WEST.ordinal, metadata))
  }

  def renderFaceYPos(block: Block, metadata: Int, renderer: RenderBlocks) {
    Tessellator.instance.setNormal(0, 1, 0)
    renderer.renderFaceYPos(block, 0, 0, 0, renderer.getBlockIconFromSideAndMetadata(block, ForgeDirection.UP.ordinal, metadata))
  }

  def renderFaceYNeg(block: Block, metadata: Int, renderer: RenderBlocks) {
    Tessellator.instance.setNormal(0, -1, 0)
    renderer.renderFaceYNeg(block, 0, 0, 0, renderer.getBlockIconFromSideAndMetadata(block, ForgeDirection.DOWN.ordinal, metadata))
  }

  def renderFaceZPos(block: Block, metadata: Int, renderer: RenderBlocks) {
    Tessellator.instance.setNormal(0, 0, 1)
    renderer.renderFaceZPos(block, 0, 0, 0, renderer.getBlockIconFromSideAndMetadata(block, ForgeDirection.SOUTH.ordinal, metadata))
  }

  def renderFaceZNeg(block: Block, metadata: Int, renderer: RenderBlocks) {
    Tessellator.instance.setNormal(0, 0, -1)
    renderer.renderFaceZNeg(block, 0, 0, 0, renderer.getBlockIconFromSideAndMetadata(block, ForgeDirection.NORTH.ordinal, metadata))
  }
}

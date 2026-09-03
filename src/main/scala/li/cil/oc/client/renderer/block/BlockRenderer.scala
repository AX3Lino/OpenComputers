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
        // Held/inventory render has no tile entity to read facing from (unlike renderWorldBlock's
        // Actuator case below), so compensate uv rotation using the block's own default unrotated
        // orientation (SOUTH = front - see common.block.Actuator's customTextures comment) instead of
        // an actual facing. Without this the top/bottom faces show vanilla's uncompensated Y+/Y- UV
        // mapping, which is what "top not at the top" in hand actually was.
        block match {
          case simple: common.block.SimpleBlock =>
            simple.setBlockBoundsForItemRender(metadata)
            simple.preItemRender(metadata)
          case _ => block.setBlockBoundsForItemRender()
        }
        renderer.setRenderBoundsFromBlock(block)
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f)

        val forward = ForgeDirection.SOUTH
        renderer.uvRotateBottom = FaceOrientation.get(ForgeDirection.DOWN, forward)
        renderer.uvRotateTop = FaceOrientation.get(ForgeDirection.UP, forward)
        renderer.uvRotateEast = FaceOrientation.get(ForgeDirection.NORTH, forward)
        renderer.uvRotateWest = FaceOrientation.get(ForgeDirection.SOUTH, forward)
        renderer.uvRotateNorth = FaceOrientation.get(ForgeDirection.WEST, forward)
        renderer.uvRotateSouth = FaceOrientation.get(ForgeDirection.EAST, forward)

        tessellator.startDrawingQuads()
        renderFaceYNeg(block, metadata, renderer)
        renderFaceYPos(block, metadata, renderer)
        renderFaceZNeg(block, metadata, renderer)
        renderFaceZPos(block, metadata, renderer)
        renderFaceXNeg(block, metadata, renderer)
        renderFaceXPos(block, metadata, renderer)
        tessellator.draw()

        renderer.uvRotateBottom = 0
        renderer.uvRotateTop = 0
        renderer.uvRotateNorth = 0
        renderer.uvRotateSouth = 0
        renderer.uvRotateWest = 0
        renderer.uvRotateEast = 0

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
        // Rotates the Side texture's arrow to point toward the actuator's facing on every face, not
        // just the one it's actually facing - see FaceOrientation for the (empirically-derived) values.
        //
        // RenderBlocks' own uvRotateXXX fields are, confusingly, NOT named after the face they affect
        // for the four lateral faces (confirmed directly in its source): renderFaceZNeg (the NORTH
        // face) reads uvRotateEast, renderFaceZPos (SOUTH) reads uvRotateWest, renderFaceXNeg (WEST)
        // reads uvRotateNorth, and renderFaceXPos (EAST) reads uvRotateSouth. Only uvRotateTop/Bottom
        // actually match their face (UP/DOWN). So the assignment below is intentionally crossed to
        // match reality, not the field names.
        val forward = actuator.facing
        renderer.uvRotateBottom = FaceOrientation.get(ForgeDirection.DOWN, forward)
        renderer.uvRotateTop = FaceOrientation.get(ForgeDirection.UP, forward)
        renderer.uvRotateEast = FaceOrientation.get(ForgeDirection.NORTH, forward)
        renderer.uvRotateWest = FaceOrientation.get(ForgeDirection.SOUTH, forward)
        renderer.uvRotateNorth = FaceOrientation.get(ForgeDirection.WEST, forward)
        renderer.uvRotateSouth = FaceOrientation.get(ForgeDirection.EAST, forward)

        val result = renderer.renderStandardBlock(block, x, y, z)

        renderer.uvRotateBottom = 0
        renderer.uvRotateTop = 0
        renderer.uvRotateNorth = 0
        renderer.uvRotateSouth = 0
        renderer.uvRotateWest = 0
        renderer.uvRotateEast = 0

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

  // The texture flip this works around only seems to occur for blocks with custom block renderers?
  def patchedRenderer(renderer: RenderBlocks, block: Block) =
    if (needsFlipping(block)) {
      val patched = patchedRenderBlocksThreadLocal.get()
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

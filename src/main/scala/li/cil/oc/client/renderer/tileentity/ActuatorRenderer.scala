package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.util.IIcon
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.util.ForgeDirection
import org.lwjgl.opengl.GL11

// Combines Adapter's per-side overlay (only open sides) with Transposer's activity blink on the same block.
object ActuatorRenderer extends TileEntitySpecialRenderer {
  override def renderTileEntityAt(tileEntity: TileEntity, x: Double, y: Double, z: Double, f: Float) {
    RenderState.checkError(getClass.getName + ".renderTileEntityAt: entering (aka: wasntme)")

    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)

    RenderState.disableLighting()
    RenderState.makeItBlend()

    GL11.glPushMatrix()

    GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5)
    GL11.glScaled(1.0025, -1.0025, 1.0025)
    GL11.glTranslatef(-0.5f, -0.5f, -0.5f)

    bindTexture(TextureMap.locationBlocksTexture)

    // Passive: one face per open side, plain alpha blend, same as AdapterRenderer.
    val openSides = tileEntity.asInstanceOf[tileentity.traits.OpenSides]
    drawOpenFaces(openSides, Textures.Actuator.iconOn)

    // Active: time-decayed additive-blend alpha since the last transfer, same as TransposerRenderer.
    val transposer = tileEntity.asInstanceOf[tileentity.traits.TransposerActivity]
    val activity = math.max(0, 1 - (System.currentTimeMillis() - transposer.lastOperation) / 1000.0)
    if (activity > 0) {
      RenderState.setBlendAlpha(activity.toFloat)
      drawAllFaces(Textures.Transposer.iconOn)
    }

    RenderState.enableLighting()

    GL11.glPopMatrix()
    GL11.glPopAttrib()

    RenderState.checkError(getClass.getName + ".renderTileEntityAt: leaving")
  }

  private def drawOpenFaces(sides: tileentity.traits.OpenSides, icon: IIcon) {
    val t = Tessellator.instance
    t.startDrawingQuads()

    if (sides.isSideOpen(ForgeDirection.DOWN)) {
      t.addVertexWithUV(0, 1, 0, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(1, 1, 0, icon.getMinU, icon.getMinV)
      t.addVertexWithUV(1, 1, 1, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(0, 1, 1, icon.getMaxU, icon.getMaxV)
    }

    if (sides.isSideOpen(ForgeDirection.UP)) {
      t.addVertexWithUV(0, 0, 0, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(0, 0, 1, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(1, 0, 1, icon.getMinU, icon.getMinV)
      t.addVertexWithUV(1, 0, 0, icon.getMinU, icon.getMaxV)
    }

    if (sides.isSideOpen(ForgeDirection.NORTH)) {
      t.addVertexWithUV(1, 1, 0, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(0, 1, 0, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(0, 0, 0, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(1, 0, 0, icon.getMinU, icon.getMinV)
    }

    if (sides.isSideOpen(ForgeDirection.SOUTH)) {
      t.addVertexWithUV(0, 1, 1, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(1, 1, 1, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(1, 0, 1, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(0, 0, 1, icon.getMinU, icon.getMinV)
    }

    if (sides.isSideOpen(ForgeDirection.WEST)) {
      t.addVertexWithUV(0, 1, 0, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(0, 1, 1, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(0, 0, 1, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(0, 0, 0, icon.getMinU, icon.getMinV)
    }

    if (sides.isSideOpen(ForgeDirection.EAST)) {
      t.addVertexWithUV(1, 1, 1, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(1, 1, 0, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(1, 0, 0, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(1, 0, 1, icon.getMinU, icon.getMinV)
    }

    t.draw()
  }

  private def drawAllFaces(icon: IIcon) {
    val t = Tessellator.instance
    t.startDrawingQuads()

    t.addVertexWithUV(0, 1, 0, icon.getMaxU, icon.getMinV)
    t.addVertexWithUV(1, 1, 0, icon.getMinU, icon.getMinV)
    t.addVertexWithUV(1, 1, 1, icon.getMinU, icon.getMaxV)
    t.addVertexWithUV(0, 1, 1, icon.getMaxU, icon.getMaxV)

    t.addVertexWithUV(0, 0, 0, icon.getMaxU, icon.getMaxV)
    t.addVertexWithUV(0, 0, 1, icon.getMaxU, icon.getMinV)
    t.addVertexWithUV(1, 0, 1, icon.getMinU, icon.getMinV)
    t.addVertexWithUV(1, 0, 0, icon.getMinU, icon.getMaxV)

    t.addVertexWithUV(1, 1, 0, icon.getMinU, icon.getMaxV)
    t.addVertexWithUV(0, 1, 0, icon.getMaxU, icon.getMaxV)
    t.addVertexWithUV(0, 0, 0, icon.getMaxU, icon.getMinV)
    t.addVertexWithUV(1, 0, 0, icon.getMinU, icon.getMinV)

    t.addVertexWithUV(0, 1, 1, icon.getMinU, icon.getMaxV)
    t.addVertexWithUV(1, 1, 1, icon.getMaxU, icon.getMaxV)
    t.addVertexWithUV(1, 0, 1, icon.getMaxU, icon.getMinV)
    t.addVertexWithUV(0, 0, 1, icon.getMinU, icon.getMinV)

    t.addVertexWithUV(0, 1, 0, icon.getMinU, icon.getMaxV)
    t.addVertexWithUV(0, 1, 1, icon.getMaxU, icon.getMaxV)
    t.addVertexWithUV(0, 0, 1, icon.getMaxU, icon.getMinV)
    t.addVertexWithUV(0, 0, 0, icon.getMinU, icon.getMinV)

    t.addVertexWithUV(1, 1, 1, icon.getMinU, icon.getMaxV)
    t.addVertexWithUV(1, 1, 0, icon.getMaxU, icon.getMaxV)
    t.addVertexWithUV(1, 0, 0, icon.getMaxU, icon.getMinV)
    t.addVertexWithUV(1, 0, 1, icon.getMinU, icon.getMinV)

    t.draw()
  }
}

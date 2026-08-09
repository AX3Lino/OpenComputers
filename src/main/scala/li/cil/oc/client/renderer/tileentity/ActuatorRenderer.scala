package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.util.ForgeDirection
import org.lwjgl.opengl.GL11

// Draws a glow overlay on the single facing side, to indicate the block's active/interfacing face.
object ActuatorRenderer extends TileEntitySpecialRenderer {
  override def renderTileEntityAt(tileEntity: TileEntity, x: Double, y: Double, z: Double, f: Float) {
    RenderState.checkError(getClass.getName + ".renderTileEntityAt: entering (aka: wasntme)")

    val actuator = tileEntity.asInstanceOf[tileentity.Actuator]

    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)

    RenderState.disableLighting()
    RenderState.makeItBlend()

    GL11.glPushMatrix()

    GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5)
    GL11.glScaled(1.0025, -1.0025, 1.0025)
    GL11.glTranslatef(-0.5f, -0.5f, -0.5f)

    bindTexture(TextureMap.locationBlocksTexture)

    drawFacingFace(actuator.facing, Textures.Actuator.iconOn)

    RenderState.enableLighting()

    GL11.glPopMatrix()
    GL11.glPopAttrib()

    RenderState.checkError(getClass.getName + ".renderTileEntityAt: leaving")
  }

  private def drawFacingFace(facing: ForgeDirection, icon: net.minecraft.util.IIcon) {
    val t = Tessellator.instance
    t.startDrawingQuads()

    facing match {
      case ForgeDirection.DOWN =>
        t.addVertexWithUV(0, 1, 0, icon.getMaxU, icon.getMinV)
        t.addVertexWithUV(1, 1, 0, icon.getMinU, icon.getMinV)
        t.addVertexWithUV(1, 1, 1, icon.getMinU, icon.getMaxV)
        t.addVertexWithUV(0, 1, 1, icon.getMaxU, icon.getMaxV)

      case ForgeDirection.UP =>
        t.addVertexWithUV(0, 0, 0, icon.getMaxU, icon.getMaxV)
        t.addVertexWithUV(0, 0, 1, icon.getMaxU, icon.getMinV)
        t.addVertexWithUV(1, 0, 1, icon.getMinU, icon.getMinV)
        t.addVertexWithUV(1, 0, 0, icon.getMinU, icon.getMaxV)

      case ForgeDirection.NORTH =>
        t.addVertexWithUV(1, 1, 0, icon.getMinU, icon.getMaxV)
        t.addVertexWithUV(0, 1, 0, icon.getMaxU, icon.getMaxV)
        t.addVertexWithUV(0, 0, 0, icon.getMaxU, icon.getMinV)
        t.addVertexWithUV(1, 0, 0, icon.getMinU, icon.getMinV)

      case ForgeDirection.SOUTH =>
        t.addVertexWithUV(0, 1, 1, icon.getMinU, icon.getMaxV)
        t.addVertexWithUV(1, 1, 1, icon.getMaxU, icon.getMaxV)
        t.addVertexWithUV(1, 0, 1, icon.getMaxU, icon.getMinV)
        t.addVertexWithUV(0, 0, 1, icon.getMinU, icon.getMinV)

      case ForgeDirection.WEST =>
        t.addVertexWithUV(0, 1, 0, icon.getMinU, icon.getMaxV)
        t.addVertexWithUV(0, 1, 1, icon.getMaxU, icon.getMaxV)
        t.addVertexWithUV(0, 0, 1, icon.getMaxU, icon.getMinV)
        t.addVertexWithUV(0, 0, 0, icon.getMinU, icon.getMinV)

      case ForgeDirection.EAST =>
        t.addVertexWithUV(1, 1, 1, icon.getMinU, icon.getMaxV)
        t.addVertexWithUV(1, 1, 0, icon.getMaxU, icon.getMaxV)
        t.addVertexWithUV(1, 0, 0, icon.getMaxU, icon.getMinV)
        t.addVertexWithUV(1, 0, 1, icon.getMinU, icon.getMinV)

      case _ =>
    }

    t.draw()
  }
}

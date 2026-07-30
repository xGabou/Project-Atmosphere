package net.Gabou.projectatmosphere.clouds.client.render.field;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Owns the color/depth framebuffer used only by the CloudField renderer. */
public final class CloudFieldRenderTargetManager {
    private static RenderTarget target;

    private CloudFieldRenderTargetManager() {
    }

    public static RenderTarget prepare(RenderTarget mainTarget, float requestedScale) {
        if (mainTarget == null || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return null;
        }

        float scale = Mth.clamp(requestedScale, 0.10F, 1.0F);
        int width = Math.max(1, Mth.ceil(mainTarget.width * scale));
        int height = Math.max(1, Mth.ceil(mainTarget.height * scale));
        if (target == null || target.width != width || target.height != height || target.getDepthTextureId() <= 0) {
            destroy();
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_NEAREST);
            configureDepthTexture(target.getDepthTextureId());
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            ProjectAtmosphere.LOGGER.info(
                    "[CloudFieldVolume] target.create scale={} main={}x{} target={}x{} color={} depth={}",
                    CloudFieldVolumeRenderStats.format(scale),
                    mainTarget.width,
                    mainTarget.height,
                    target.width,
                    target.height,
                    target.getColorTextureId(),
                    target.getDepthTextureId()
            );
        }
        return target;
    }

    public static void clearAndBind(RenderTarget cloudTarget) {
        if (cloudTarget == null) {
            return;
        }
        RenderSystem.disableScissor();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        cloudTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        cloudTarget.clear(Minecraft.ON_OSX);
        // RenderTarget.clear unbinds the FBO. Rebind with an explicit viewport.
        cloudTarget.bindWrite(true);
    }

    public static void onResize() {
        destroy();
    }

    public static void onLevelChanged() {
        destroy();
    }

    public static void shutdown() {
        destroy();
    }

    public static String diagnostics(RenderTarget mainTarget) {
        if (target == null) {
            return "cloudFieldTarget=none";
        }
        return "cloudFieldTarget=" + target.width + "x" + target.height
                + " view=" + target.viewWidth + "x" + target.viewHeight
                + " volumeViewport=" + target.viewWidth + "x" + target.viewHeight
                + " color=" + target.getColorTextureId()
                + " depth=" + target.getDepthTextureId()
                + " main=" + (mainTarget == null
                        ? "none"
                        : mainTarget.width + "x" + mainTarget.height
                                + " mainView=" + mainTarget.viewWidth + "x" + mainTarget.viewHeight
                                + " compositeViewport=" + mainTarget.viewWidth + "x" + mainTarget.viewHeight)
                + " colorFilter=nearest depthFilter=nearest";
    }

    private static void configureDepthTexture(int textureId) {
        if (textureId <= 0) {
            return;
        }
        RenderSystem.bindTexture(textureId);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.bindTexture(0);
    }

    private static void destroy() {
        if (target == null) {
            return;
        }
        target.destroyBuffers();
        target = null;
    }
}

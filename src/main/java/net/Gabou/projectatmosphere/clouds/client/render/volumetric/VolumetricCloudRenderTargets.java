package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * Owns all offscreen targets of the volumetric cloud pipeline: the weather
 * map, the ping-pong raymarch color+depth targets used for temporal
 * reprojection, and the ground shadow transmittance map.
 */
public final class VolumetricCloudRenderTargets {
    private static RenderTarget weatherTarget;
    private static RenderTarget morphologyTarget;
    private static RenderTarget shadowTarget;
    private static final RenderTarget[] cloudTargets = new RenderTarget[2];
    private static int currentIndex;
    private static boolean historyValid;

    private VolumetricCloudRenderTargets() {
    }

    public static RenderTarget prepareWeatherTarget(int size) {
        if (weatherTarget == null || weatherTarget.width != size) {
            if (weatherTarget != null) {
                weatherTarget.destroyBuffers();
            }
            weatherTarget = new TextureTarget(size, size, false, Minecraft.ON_OSX);
            weatherTarget.setFilterMode(GL11.GL_LINEAR);
            configureClamp(weatherTarget.getColorTextureId());
            weatherTarget.setClearColor(0.0F, 0.35F, 0.45F, 0.0F);
        }
        return weatherTarget;
    }

    public static RenderTarget prepareShadowTarget(int size) {
        if (shadowTarget == null || shadowTarget.width != size) {
            if (shadowTarget != null) {
                shadowTarget.destroyBuffers();
            }
            shadowTarget = new TextureTarget(size, size, false, Minecraft.ON_OSX);
            shadowTarget.setFilterMode(GL11.GL_LINEAR);
            configureClamp(shadowTarget.getColorTextureId());
            shadowTarget.setClearColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        return shadowTarget;
    }

    public static RenderTarget shadowTargetOrNull() {
        return shadowTarget;
    }

    public static RenderTarget prepareMorphologyTarget(int size) {
        if (morphologyTarget == null || morphologyTarget.width != size) {
            if (morphologyTarget != null) {
                morphologyTarget.destroyBuffers();
            }
            morphologyTarget = new TextureTarget(size, size, false, Minecraft.ON_OSX);
            morphologyTarget.setFilterMode(GL11.GL_LINEAR);
            configureClamp(morphologyTarget.getColorTextureId());
            morphologyTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
        return morphologyTarget;
    }

    public static RenderTarget weatherTargetOrNull() {
        return weatherTarget;
    }

    public static RenderTarget morphologyTargetOrNull() {
        return morphologyTarget;
    }

    /**
     * Prepares the pair of raymarch targets at the requested scale of the main
     * target. Invalidate history on any resize.
     */
    public static boolean prepareCloudTargets(RenderTarget mainTarget, float scale) {
        if (mainTarget == null || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return false;
        }
        float safeScale = Mth.clamp(scale, 0.10F, 1.0F);
        int width = Math.max(1, Mth.ceil(mainTarget.width * safeScale));
        int height = Math.max(1, Mth.ceil(mainTarget.height * safeScale));
        RenderTarget current = cloudTargets[0];
        if (current == null || current.width != width || current.height != height) {
            destroyCloudTargets();
            for (int i = 0; i < 2; i++) {
                RenderTarget target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
                target.setFilterMode(GL11.GL_LINEAR);
                configureClamp(target.getColorTextureId());
                upgradeColorToRgba16f(target.getColorTextureId(), width, height);
                configureDepth(target.getDepthTextureId());
                target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                cloudTargets[i] = target;
            }
            historyValid = false;
            currentIndex = 0;
        }
        return true;
    }

    public static RenderTarget currentCloudTarget() {
        return cloudTargets[currentIndex];
    }

    public static RenderTarget historyCloudTarget() {
        return cloudTargets[1 - currentIndex];
    }

    public static boolean isHistoryValid() {
        return historyValid && historyCloudTarget() != null;
    }

    public static void swapAndMarkHistoryValid() {
        currentIndex = 1 - currentIndex;
        historyValid = true;
    }

    public static void invalidateHistory() {
        historyValid = false;
    }

    public static void clearAndBind(RenderTarget target) {
        if (target == null) {
            return;
        }
        RenderSystem.disableScissor();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
    }

    public static void onResize() {
        destroyAll();
    }

    public static void onLevelChanged() {
        destroyAll();
    }

    public static void shutdown() {
        destroyAll();
    }

    private static void destroyAll() {
        CloudWeatherMapRenderer.invalidateCache();
        if (weatherTarget != null) {
            weatherTarget.destroyBuffers();
            weatherTarget = null;
        }
        if (morphologyTarget != null) {
            morphologyTarget.destroyBuffers();
            morphologyTarget = null;
        }
        if (shadowTarget != null) {
            shadowTarget.destroyBuffers();
            shadowTarget = null;
        }
        destroyCloudTargets();
    }

    private static void destroyCloudTargets() {
        for (int i = 0; i < 2; i++) {
            if (cloudTargets[i] != null) {
                cloudTargets[i].destroyBuffers();
                cloudTargets[i] = null;
            }
        }
        historyValid = false;
    }

    /**
     * Re-specs a TextureTarget's RGBA8 color texture as RGBA16F. The cloud
     * buffers hold premultiplied color that the composite divides by alpha;
     * 8-bit quantization amplified by 1/alpha shows as speckle at soft cloud
     * edges, and repeated temporal blends drift in 8 bits. The FBO attachment
     * references the texture object, so a level-0 respec is picked up as-is.
     */
    private static void upgradeColorToRgba16f(int textureId, int width, int height) {
        if (textureId <= 0) {
            return;
        }
        RenderSystem.bindTexture(textureId);
        GlStateManager._texImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_RGBA16F,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL30.GL_HALF_FLOAT,
                null
        );
        RenderSystem.bindTexture(0);
    }

    private static void configureClamp(int textureId) {
        if (textureId <= 0) {
            return;
        }
        RenderSystem.bindTexture(textureId);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.bindTexture(0);
    }

    private static void configureDepth(int textureId) {
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
}

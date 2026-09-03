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
    private static RenderTarget cumulusStageSupportTarget;
    private static RenderTarget cumulusStageBaseTarget;
    private static RenderTarget cumulusStageTopTarget;
    private static RenderTarget stormCandidateTarget;
    private static RenderTarget stormDescriptorTarget;
    private static RenderTarget puffCandidateTarget;
    private static RenderTarget shadowTarget;
    /** T153-only ground-truth interval map; never sampled by production. */
    private static RenderTarget visibleVolumeOracleTarget;
    private static final RenderTarget[] cloudTargets = new RenderTarget[2];
    private static int currentIndex;
    private static boolean historyValid;
    private static long resolutionGeneration = 1L;

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

    /** Eight packed native-PUFF descriptor indices per conservative world tile. */
    public static RenderTarget preparePuffCandidateTarget() {
        int size = PuffLobeSpatialIndex.GRID_SIZE;
        if (puffCandidateTarget == null || puffCandidateTarget.width != size) {
            if (puffCandidateTarget != null) {
                puffCandidateTarget.destroyBuffers();
            }
            puffCandidateTarget = createHalfFloatMap(size);
            // Packed integer digits must never interpolate across tile edges.
            puffCandidateTarget.setFilterMode(GL11.GL_NEAREST);
        }
        return puffCandidateTarget;
    }

    /** Eight packed direct-storm descriptor indices per conservative world tile. */
    public static RenderTarget prepareStormCandidateTarget() {
        int size = StormLobeSpatialIndex.GRID_SIZE;
        if (stormCandidateTarget == null || stormCandidateTarget.width != size) {
            if (stormCandidateTarget != null) {
                stormCandidateTarget.destroyBuffers();
            }
            stormCandidateTarget = createFloatMap(size, size, GL11.GL_NEAREST);
        }
        return stormCandidateTarget;
    }

    /** Four RGBA32F texels for each of the bounded 64 direct-storm lobes. */
    public static RenderTarget prepareStormDescriptorTarget() {
        int width = StormLobeSpatialIndex.DESCRIPTOR_WIDTH;
        int height = StormLobeSpatialIndex.DESCRIPTOR_HEIGHT;
        if (stormDescriptorTarget == null
                || stormDescriptorTarget.width != width
                || stormDescriptorTarget.height != height) {
            if (stormDescriptorTarget != null) {
                stormDescriptorTarget.destroyBuffers();
            }
            stormDescriptorTarget = createFloatMap(width, height, GL11.GL_NEAREST);
        }
        return stormDescriptorTarget;
    }

    /** Independent BASE/CORE/TOWER/CROWN supports for native profile-3 clouds. */
    public static RenderTarget prepareCumulusStageSupportTarget(int size) {
        if (cumulusStageSupportTarget == null || cumulusStageSupportTarget.width != size) {
            if (cumulusStageSupportTarget != null) {
                cumulusStageSupportTarget.destroyBuffers();
            }
            cumulusStageSupportTarget = createHalfFloatMap(size);
        }
        return cumulusStageSupportTarget;
    }

    /** Premultiplied BASE/CORE/TOWER/CROWN lower endpoints. */
    public static RenderTarget prepareCumulusStageBaseTarget(int size) {
        if (cumulusStageBaseTarget == null || cumulusStageBaseTarget.width != size) {
            if (cumulusStageBaseTarget != null) {
                cumulusStageBaseTarget.destroyBuffers();
            }
            cumulusStageBaseTarget = createHalfFloatMap(size);
        }
        return cumulusStageBaseTarget;
    }

    /** Premultiplied BASE/CORE/TOWER/CROWN upper endpoints. */
    public static RenderTarget prepareCumulusStageTopTarget(int size) {
        if (cumulusStageTopTarget == null || cumulusStageTopTarget.width != size) {
            if (cumulusStageTopTarget != null) {
                cumulusStageTopTarget.destroyBuffers();
            }
            cumulusStageTopTarget = createHalfFloatMap(size);
        }
        return cumulusStageTopTarget;
    }

    public static RenderTarget weatherTargetOrNull() {
        return weatherTarget;
    }

    public static RenderTarget morphologyTargetOrNull() {
        return morphologyTarget;
    }

    public static RenderTarget cumulusStageSupportTargetOrNull() {
        return cumulusStageSupportTarget;
    }

    public static RenderTarget cumulusStageBaseTargetOrNull() {
        return cumulusStageBaseTarget;
    }

    public static RenderTarget cumulusStageTopTargetOrNull() {
        return cumulusStageTopTarget;
    }

    public static RenderTarget puffCandidateTargetOrNull() {
        return puffCandidateTarget;
    }

    public static RenderTarget stormCandidateTargetOrNull() {
        return stormCandidateTarget;
    }

    public static RenderTarget stormDescriptorTargetOrNull() {
        return stormDescriptorTarget;
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
            resolutionGeneration++;
        }
        return true;
    }

    public static RenderTarget currentCloudTarget() {
        return cloudTargets[currentIndex];
    }

    public static RenderTarget historyCloudTarget() {
        return cloudTargets[1 - currentIndex];
    }

    /**
     * Allocates the T153 diagnostic oracle publication target. Four horizontal
     * banks provide sixteen packed occupied intervals per production pixel.
     * Each packed interval also carries the alpha-98 optical cutoff. The target exists
     * only while an oracle arm is selected and is excluded from production
     * history and timing.
     */
    public static RenderTarget prepareVisibleVolumeOracleTarget(int width, int height) {
        int oracleWidth = Math.max(1, width) * 4;
        int oracleHeight = Math.max(1, height);
        if (visibleVolumeOracleTarget == null
                || visibleVolumeOracleTarget.width != oracleWidth
                || visibleVolumeOracleTarget.height != oracleHeight) {
            if (visibleVolumeOracleTarget != null) {
                visibleVolumeOracleTarget.destroyBuffers();
            }
            visibleVolumeOracleTarget = new TextureTarget(
                    oracleWidth, oracleHeight, true, Minecraft.ON_OSX);
            visibleVolumeOracleTarget.setFilterMode(GL11.GL_NEAREST);
            configureClamp(visibleVolumeOracleTarget.getColorTextureId());
            upgradeColorToRgba32f(
                    visibleVolumeOracleTarget.getColorTextureId(), oracleWidth, oracleHeight);
            configureDepth(visibleVolumeOracleTarget.getDepthTextureId());
            visibleVolumeOracleTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
        return visibleVolumeOracleTarget;
    }

    public static void releaseVisibleVolumeOracleTarget() {
        if (visibleVolumeOracleTarget != null) {
            visibleVolumeOracleTarget.destroyBuffers();
            visibleVolumeOracleTarget = null;
        }
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
        if (cumulusStageSupportTarget != null) {
            cumulusStageSupportTarget.destroyBuffers();
            cumulusStageSupportTarget = null;
        }
        if (cumulusStageBaseTarget != null) {
            cumulusStageBaseTarget.destroyBuffers();
            cumulusStageBaseTarget = null;
        }
        if (cumulusStageTopTarget != null) {
            cumulusStageTopTarget.destroyBuffers();
            cumulusStageTopTarget = null;
        }
        if (stormCandidateTarget != null) {
            stormCandidateTarget.destroyBuffers();
            stormCandidateTarget = null;
        }
        if (stormDescriptorTarget != null) {
            stormDescriptorTarget.destroyBuffers();
            stormDescriptorTarget = null;
        }
        if (puffCandidateTarget != null) {
            puffCandidateTarget.destroyBuffers();
            puffCandidateTarget = null;
        }
        if (shadowTarget != null) {
            shadowTarget.destroyBuffers();
            shadowTarget = null;
        }
        if (visibleVolumeOracleTarget != null) {
            visibleVolumeOracleTarget.destroyBuffers();
            visibleVolumeOracleTarget = null;
        }
        destroyCloudTargets();
        StormGeometryBuildCoordinator.reset();
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

    private static RenderTarget createHalfFloatMap(int size) {
        RenderTarget target = new TextureTarget(size, size, false, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
        configureClamp(target.getColorTextureId());
        upgradeColorToRgba16f(target.getColorTextureId(), size, size);
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        return target;
    }

    static long resolutionGeneration() {
        return resolutionGeneration;
    }

    private static RenderTarget createFloatMap(int width, int height, int filterMode) {
        RenderTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        target.setFilterMode(filterMode);
        configureClamp(target.getColorTextureId());
        upgradeColorToRgba32f(target.getColorTextureId(), width, height);
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        return target;
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
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
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
        } finally {
            RenderSystem.bindTexture(previousTexture);
        }
    }

    private static void upgradeColorToRgba32f(int textureId, int width, int height) {
        if (textureId <= 0) {
            return;
        }
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            RenderSystem.bindTexture(textureId);
            GlStateManager._texImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL30.GL_RGBA32F,
                    width,
                    height,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_FLOAT,
                    null
            );
        } finally {
            RenderSystem.bindTexture(previousTexture);
        }
    }

    private static void configureClamp(int textureId) {
        if (textureId <= 0) {
            return;
        }
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            RenderSystem.bindTexture(textureId);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } finally {
            RenderSystem.bindTexture(previousTexture);
        }
    }

    private static void configureDepth(int textureId) {
        if (textureId <= 0) {
            return;
        }
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            RenderSystem.bindTexture(textureId);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } finally {
            RenderSystem.bindTexture(previousTexture);
        }
    }
}

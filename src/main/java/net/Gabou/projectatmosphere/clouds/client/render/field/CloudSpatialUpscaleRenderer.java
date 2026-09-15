package net.Gabou.projectatmosphere.clouds.client.render.field;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.clouds.client.render.CloudGpuTimer;
import net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthFrame;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.FullscreenQuad;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudDebugConfig;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderTargets;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;

/**
 * T209. The spatial upscale of the temporal lattice to the display.
 *
 * <p>Runs after the temporal resolve and before the composite: the 960x540
 * premultiplied lattice (RGBA16F + depth) is reconstructed at the display
 * size into its own layer by one of three methods ({@code cloud_spatial_upscale.fsh}:
 * 0 the fused composite's PA bilinear as a control, 1 EASU on the
 * premultiplied field, 2 a depth-guided Catmull-Rom), and the composite then
 * reads that layer one-to-one ({@code OneToOne}) - the layer is never filtered
 * a second time. Its clock is its own, so the pass is priced alone.
 *
 * <p>Off ({@link VolumetricCloudDebugConfig#spatialUpscaleMethod()} negative)
 * the composite reads the lattice directly, as T207 shipped it. A pass that
 * cannot run returns null and the caller composites the lattice directly, so
 * the frame is never left without clouds.
 */
public final class CloudSpatialUpscaleRenderer {
    private static final CloudGpuTimer GPU_TIMER = new CloudGpuTimer();
    private static float lastGpuMilliseconds = -1.0F;
    private static boolean lastFrameUpscaled;
    private static int lastMethod = -1;

    private CloudSpatialUpscaleRenderer() {
    }

    /**
     * Upscales {@code source} to {@code width x height}. Returns the layer,
     * or null when the pass could not run.
     */
    public static RenderTarget upscale(
            RenderTarget source,
            int width,
            int height,
            SceneDepthFrame sceneDepth,
            int method
    ) {
        lastFrameUpscaled = false;
        lastMethod = -1;
        ShaderInstance shader = CloudFieldVolumeShaders.getSpatialUpscaleShader();
        if (shader == null || source == null || source.getDepthTextureId() <= 0
                || width <= 0 || height <= 0 || method < 0) {
            return null;
        }
        RenderTarget layer = VolumetricCloudRenderTargets.prepareSpatialUpscaleTarget(width, height);
        if (layer == null) {
            return null;
        }
        SceneDepthFrame safeSceneDepth = sceneDepth == null ? SceneDepthFrame.INVALID : sceneDepth;
        boolean guided = safeSceneDepth.valid() && safeSceneDepth.detached();

        GPU_TIMER.poll();
        lastGpuMilliseconds = GPU_TIMER.isSupported() && GPU_TIMER.hasResult()
                ? GPU_TIMER.getLastMilliseconds() : -1.0F;
        VolumetricCloudRenderTargets.clearAndBind(layer);
        RenderSystem.disableBlend();
        // Depth is data here, and GL writes it only through an enabled test:
        // GL_ALWAYS writes without rejecting (T164).
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        shader.setSampler("CloudColorSampler", source.getColorTextureId());
        shader.setSampler("CloudDepthSampler", source.getDepthTextureId());
        shader.setSampler("SceneDepthSampler", guided ? safeSceneDepth.textureId() : 0);
        shader.safeGetUniform("SceneDepthValid").set(guided ? 1 : 0);
        shader.safeGetUniform("CoverageAlphaReconstruction").set(
                VolumetricCloudDebugConfig.coverageAlphaReconstruction() ? 1 : 0);
        shader.safeGetUniform("UpscaleMethod").set(method);
        shader.apply();
        GPU_TIMER.begin();
        try {
            FullscreenQuad.draw(shader);
        } finally {
            GPU_TIMER.end();
            shader.clear();
        }
        lastFrameUpscaled = true;
        lastMethod = method;
        return layer;
    }

    /** Milliseconds the last upscale cost on the GPU, or -1 with no result. */
    public static float lastGpuMilliseconds() {
        return lastGpuMilliseconds;
    }

    public static boolean lastFrameUpscaled() {
        return lastFrameUpscaled;
    }

    public static int lastMethod() {
        return lastMethod;
    }

    public static void markUnused() {
        lastFrameUpscaled = false;
        lastMethod = -1;
        lastGpuMilliseconds = -1.0F;
    }

    public static void shutdown() {
        GPU_TIMER.close();
        lastFrameUpscaled = false;
        lastMethod = -1;
        lastGpuMilliseconds = -1.0F;
    }
}

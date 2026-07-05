package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.Gabou.projectatmosphere.clouds.client.render.CloudGpuTimer;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderStateGuard;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * Fullscreen global raymarch pass over the weather-map-driven density field,
 * with temporal reprojection into ping-pong history targets and a frame-time
 * governor that scales the step budget under GPU pressure.
 */
public final class VolumetricCloudRenderer {
    private static final CloudGpuTimer GPU_TIMER = new CloudGpuTimer();
    private static final CloudFrameTimeGovernor GOVERNOR = new CloudFrameTimeGovernor();

    private static final Matrix4f prevProj = new Matrix4f();
    private static final Matrix4f prevViewRot = new Matrix4f();
    private static final Vector3f prevCameraPos = new Vector3f();
    private static boolean hasPrevFrame;
    private static long frameIndex;
    private static volatile float lastGpuMilliseconds = -1.0F;
    private static volatile boolean lastHistoryValid;

    private VolumetricCloudRenderer() {
    }

    public static float lastGpuMilliseconds() {
        return lastGpuMilliseconds;
    }

    public static float governorStepScale() {
        return GOVERNOR.stepScale();
    }

    public static void invalidateHistory() {
        hasPrevFrame = false;
        lastHistoryValid = false;
        VolumetricCloudRenderTargets.invalidateHistory();
    }

    /** Whether the last raymarch consumed temporal history (for status/logs). */
    public static boolean lastHistoryValid() {
        return lastHistoryValid;
    }

    /**
     * Source-aware raymarch tuning. Spawned CloudField clouds need a denser
     * extinction response than the autonomous cell simulation: their weather
     * map coverage comes from many small cloudlets, so the same extinction
     * scale that suits broad cells renders them mostly see-through. History
     * blend stays below the old 0.88 so reprojection cannot ghost-smear the
     * noise detail away.
     */
    public record Tuning(
            float densityMul,
            float coverageMul,
            float extinctionScale,
            float historyBlend
    ) {
        public static final Tuning FIELDS = new Tuning(1.45F, 1.25F, 0.115F, 0.85F);
        public static final Tuning CELLS = new Tuning(1.05F, 1.25F, 0.055F, 0.85F);

        public String summary() {
            return String.format(
                    java.util.Locale.ROOT,
                    "density=%.2f coverage=%.2f extinction=%.3f historyBlend=%.2f",
                    densityMul, coverageMul, extinctionScale, historyBlend);
        }
    }

    /**
     * Raymarches the cloud field into the current cloud target.
     *
     * @return true when the pass rendered and the target holds this frame's clouds
     */
    public static boolean render(
            RenderTarget mainTarget,
            CloudWeatherMapRenderer.Result weather,
            VolumetricCloudLighting.Frame lighting,
            Matrix4f projection,
            Matrix4f viewRotation,
            Vector3f cameraPos,
            float worldTimeTicks,
            Vector3f windVec,
            VolumetricQualityProfile profile,
            float maxRenderDistance,
            FunnelUniforms funnels,
            Tuning tuning,
            boolean sceneRayLimitEnabled
    ) {
        ShaderInstance shader = VolumetricCloudShaders.volumeShader();
        if (shader == null || mainTarget == null || !weather.rendered()) {
            return false;
        }
        if (!CloudNoiseTextureManager.ensureReady()) {
            return false;
        }
        if (!VolumetricCloudRenderTargets.prepareCloudTargets(mainTarget, profile.resolutionScale())) {
            return false;
        }

        GPU_TIMER.poll();
        lastGpuMilliseconds = GPU_TIMER.getLastMilliseconds();
        float stepScale = GOVERNOR.update(lastGpuMilliseconds);

        // Camera cuts (teleports) poison reprojection; detect and reset.
        if (hasPrevFrame && prevCameraPos.distance(cameraPos) > 24.0F) {
            invalidateHistory();
        }

        RenderTarget cloudTarget = VolumetricCloudRenderTargets.currentCloudTarget();
        RenderTarget historyTarget = VolumetricCloudRenderTargets.historyCloudTarget();
        // History is only consumed when the ping-pong target actually holds
        // last frame's clouds and no camera cut/resize/no-cloud frame
        // invalidated it since. Everything else ghosts.
        Tuning safeTuning = tuning == null ? Tuning.CELLS : tuning;
        boolean historyValid = profile.temporalEnabled()
                && hasPrevFrame
                && historyTarget != null
                && VolumetricCloudRenderTargets.isHistoryValid();
        lastHistoryValid = historyValid;

        Matrix4f invProj = new Matrix4f(projection).invert();
        Matrix4f invViewRot = new Matrix4f(viewRotation).invert();
        Matrix4f prevViewProj = new Matrix4f(prevProj)
                .mul(prevViewRot)
                .translate(
                        cameraPos.x - prevCameraPos.x,
                        cameraPos.y - prevCameraPos.y,
                        cameraPos.z - prevCameraPos.z
                );

        RenderTarget weatherTarget = VolumetricCloudRenderTargets.prepareWeatherTarget(profile.weatherMapSize());

        VolumetricCloudRenderTargets.clearAndBind(cloudTarget);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        shader.setSampler("WeatherMapSampler", weatherTarget.getColorTextureId());
        shader.setSampler("BlueNoiseSampler", CloudNoiseTextureManager.blueNoiseTextureId());
        shader.setSampler("SceneDepthSampler", Math.max(0, mainTarget.getDepthTextureId()));
        shader.setSampler("HistorySampler", historyValid ? historyTarget.getColorTextureId() : 0);

        shader.safeGetUniform("CloudProjMat").set(projection);
        shader.safeGetUniform("ViewRotMat").set(viewRotation);
        shader.safeGetUniform("InvProjMat").set(invProj);
        shader.safeGetUniform("InvViewRotMat").set(invViewRot);
        shader.safeGetUniform("PrevViewProjMat").set(prevViewProj);
        shader.safeGetUniform("CameraPos").set(cameraPos.x, cameraPos.y, cameraPos.z);
        shader.safeGetUniform("OutputSize").set((float) cloudTarget.width, (float) cloudTarget.height);
        shader.safeGetUniform("WeatherOrigin").set((float) weather.originX(), (float) weather.originZ());
        shader.safeGetUniform("WeatherExtent").set(CloudWeatherMapRenderer.WEATHER_EXTENT);
        shader.safeGetUniform("SlabBaseY").set(weather.slabBaseY());
        shader.safeGetUniform("SlabTopY").set(weather.slabTopY());
        Vector3f lightDir = lighting.lightDirection();
        Vector3f lightColor = lighting.lightColor();
        Vector3f ambientTop = lighting.ambientTop();
        Vector3f ambientBottom = lighting.ambientBottom();
        shader.safeGetUniform("LightDir").set(lightDir.x, lightDir.y, lightDir.z);
        shader.safeGetUniform("LightColor").set(lightColor.x, lightColor.y, lightColor.z);
        shader.safeGetUniform("AmbientTop").set(ambientTop.x, ambientTop.y, ambientTop.z);
        shader.safeGetUniform("AmbientBottom").set(ambientBottom.x, ambientBottom.y, ambientBottom.z);
        shader.safeGetUniform("SunsetStrength").set(lighting.sunsetStrength());
        shader.safeGetUniform("NightFactor").set(lighting.nightFactor());
        shader.safeGetUniform("StormDarkening").set(lighting.stormDarkening());
        shader.safeGetUniform("WindVec").set(windVec.x, windVec.y, windVec.z);
        shader.safeGetUniform("WorldTime").set(worldTimeTicks);
        shader.safeGetUniform("FrameIndex").set((float) (frameIndex % 1024L));
        shader.safeGetUniform("RaymarchSteps").set(profile.raymarchSteps());
        shader.safeGetUniform("LightSteps").set(profile.lightSteps());
        shader.safeGetUniform("ScatterOctaves").set(profile.scatterOctaves());
        shader.safeGetUniform("DetailQuality").set(profile.detailQuality());
        shader.safeGetUniform("StepScale").set(stepScale);
        shader.safeGetUniform("MaxRenderDistance").set(Math.max(300.0F, maxRenderDistance));
        shader.safeGetUniform("UseSceneDepth").set(sceneRayLimitEnabled && mainTarget.getDepthTextureId() > 0 ? 1 : 0);
        shader.safeGetUniform("CoveragePretestEnabled").set(VolumetricCloudDebugConfig.coveragePretestEnabled() ? 1 : 0);
        shader.safeGetUniform("CoveragePretestSamples").set(VolumetricCloudDebugConfig.coveragePretestSamples());
        shader.safeGetUniform("CoveragePretestThreshold").set(VolumetricCloudDebugConfig.coveragePretestThreshold());
        shader.safeGetUniform("CoveragePretestDilation").set(VolumetricCloudDebugConfig.coveragePretestDilation());
        shader.safeGetUniform("HistoryValid").set(historyValid ? 1 : 0);
        shader.safeGetUniform("HistoryBlend").set(historyValid ? safeTuning.historyBlend() : 0.0F);
        shader.safeGetUniform("DensityMul").set(safeTuning.densityMul());
        shader.safeGetUniform("CoverageMul").set(safeTuning.coverageMul());
        shader.safeGetUniform("ExtinctionScale").set(safeTuning.extinctionScale());
        FunnelUniforms safeFunnels = funnels == null ? FunnelUniforms.NONE : funnels;
        shader.safeGetUniform("FunnelCount").set(safeFunnels.count());
        shader.safeGetUniform("Funnel0A").set(safeFunnels.f0a());
        shader.safeGetUniform("Funnel0B").set(safeFunnels.f0b());
        shader.safeGetUniform("Funnel1A").set(safeFunnels.f1a());
        shader.safeGetUniform("Funnel1B").set(safeFunnels.f1b());

        shader.apply();
        bind3dNoise(shader);
        GPU_TIMER.begin();
        try {
            FullscreenQuad.draw(shader);
        } finally {
            GPU_TIMER.end();
            unbind3dNoise();
            shader.clear();
            CloudRenderStateGuard.restoreAfterCloudPass(mainTarget);
        }

        prevProj.set(projection);
        prevViewRot.set(viewRotation);
        prevCameraPos.set(cameraPos);
        hasPrevFrame = true;
        frameIndex++;
        return true;
    }

    /** Marks the just-rendered target as history for the next frame. */
    public static void finishFrame() {
        VolumetricCloudRenderTargets.swapAndMarkHistoryValid();
    }

    public static void resetGovernor() {
        GOVERNOR.reset();
    }

    /**
     * The vanilla sampler system only binds 2D textures, so the two 3D noise
     * textures are bound manually on units above the JSON-declared samplers.
     */
    private static void bind3dNoise(ShaderInstance shader) {
        int program = shader.getId();
        bind3dSampler(program, "BaseNoiseSampler", 8, CloudNoiseTextureManager.baseTextureId());
        bind3dSampler(program, "DetailNoiseSampler", 9, CloudNoiseTextureManager.detailTextureId());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private static void bind3dSampler(int program, String name, int unit, int textureId) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0 || textureId <= 0) {
            return;
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, textureId);
        GL20.glUniform1i(location, unit);
    }

    private static void unbind3dNoise() {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 8);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 9);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    /** Analytic funnel uniform payload (tornado readiness slot). */
    public record FunnelUniforms(
            int count,
            float[] f0a,
            float[] f0b,
            float[] f1a,
            float[] f1b
    ) {
        public static final FunnelUniforms NONE = new FunnelUniforms(
                0, new float[4], new float[4], new float[4], new float[4]);
    }
}

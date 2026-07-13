package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.Gabou.projectatmosphere.clouds.client.render.CloudGpuTimer;
import net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthFrame;
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
    private static volatile float lastHistoryConfidence;
    private static volatile float lastResolutionScale = 1.0F;
    private static boolean denseCameraResolution;
    private static int fragmentTextureUnits = -1;

    private VolumetricCloudRenderer() {
    }

    public static float lastGpuMilliseconds() {
        return lastGpuMilliseconds;
    }

    public static float governorStepScale() {
        return GOVERNOR.stepScale();
    }

    public static float lastResolutionScale() {
        return lastResolutionScale;
    }

    public static void invalidateHistory() {
        hasPrevFrame = false;
        lastHistoryValid = false;
        VolumetricCloudRenderTargets.invalidateHistory();
    }

    /** Clears timing and temporal state owned by the native volumetric pass. */
    public static void shutdown() {
        invalidateHistory();
        GOVERNOR.reset();
        GPU_TIMER.close();
        lastGpuMilliseconds = -1.0F;
        lastHistoryValid = false;
        lastHistoryConfidence = 0.0F;
        lastResolutionScale = 1.0F;
        denseCameraResolution = false;
    }

    /** Whether the last raymarch consumed temporal history (for status/logs). */
    public static boolean lastHistoryValid() {
        return lastHistoryValid;
    }

    public static float lastHistoryConfidence() {
        return lastHistoryConfidence;
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
            SceneDepthFrame sceneDepth,
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
        if (!hasTextureUnitCapacity()) {
            return false;
        }
        ShaderInstance shader = VolumetricCloudShaders.volumeShader();
        if (shader == null || mainTarget == null || !weather.rendered()) {
            return false;
        }
        if (!CloudNoiseTextureManager.ensureReady()) {
            return false;
        }
        float cameraCloudDensity = CameraCloudDensityTracker.smoothedCameraDensity();
        // ULTRA's 0.75 target exceeds one million fragments at 1080p. Dense
        // whiteout cannot resolve that extra sampling, so use 0.50 only while
        // the canonical camera density confirms an interior view. Hysteresis
        // prevents repeated target rebuilds at the cloud boundary.
        if (denseCameraResolution) {
            if (cameraCloudDensity < 0.04F) {
                denseCameraResolution = false;
            }
        } else if (cameraCloudDensity > 0.12F) {
            denseCameraResolution = true;
        }
        float resolutionScale = denseCameraResolution
                ? Math.min(profile.resolutionScale(), 0.50F)
                : profile.resolutionScale();
        lastResolutionScale = resolutionScale;
        if (!VolumetricCloudRenderTargets.prepareCloudTargets(mainTarget, resolutionScale)) {
            return false;
        }

        GPU_TIMER.poll();
        lastGpuMilliseconds = GPU_TIMER.getLastMilliseconds();
        float stepScale = GOVERNOR.update(lastGpuMilliseconds);

        // Camera cuts and rapid turns poison reprojection. Gentle movement
        // scales history confidence continuously instead of toggling it.
        float historyConfidence = 1.0F;
        if (hasPrevFrame) {
            float cameraDistance = prevCameraPos.distance(cameraPos);
            float rotationDelta = rotationDelta(prevViewRot, viewRotation);
            if (cameraDistance > 24.0F || rotationDelta > 0.22F) {
                invalidateHistory();
                historyConfidence = 0.0F;
            } else {
                historyConfidence *= 1.0F - smoothstep(4.0F, 24.0F, cameraDistance);
                historyConfidence *= 1.0F - smoothstep(0.025F, 0.22F, rotationDelta);
            }
        }
        historyConfidence *= 1.0F - smoothstep(0.04F, 0.48F, cameraCloudDensity);

        RenderTarget cloudTarget = VolumetricCloudRenderTargets.currentCloudTarget();
        RenderTarget historyTarget = VolumetricCloudRenderTargets.historyCloudTarget();
        // History is only consumed when the ping-pong target actually holds
        // last frame's clouds and no camera cut/resize/no-cloud frame
        // invalidated it since. Everything else ghosts.
        Tuning safeTuning = tuning == null ? Tuning.CELLS : tuning;
        boolean historyValid = VolumetricCloudDebugConfig.historyEnabled()
                && profile.temporalEnabled()
                && hasPrevFrame
                && historyTarget != null
                && VolumetricCloudRenderTargets.isHistoryValid();
        lastHistoryValid = historyValid;
        lastHistoryConfidence = historyValid ? historyConfidence : 0.0F;

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
        RenderTarget morphologyTarget = VolumetricCloudRenderTargets.prepareMorphologyTarget(profile.weatherMapSize());
        RenderTarget stormStructureTarget =
                VolumetricCloudRenderTargets.prepareStormStructureTarget(profile.weatherMapSize());
        RenderTarget stormLayerHeightTarget =
                VolumetricCloudRenderTargets.prepareStormLayerHeightTarget(profile.weatherMapSize());
        RenderTarget stormTowerTarget =
                VolumetricCloudRenderTargets.prepareStormTowerTarget(profile.weatherMapSize());
        if (weatherTarget == null || morphologyTarget == null
                || stormStructureTarget == null || stormLayerHeightTarget == null
                || stormTowerTarget == null) {
            return false;
        }

        VolumetricCloudRenderTargets.clearAndBind(cloudTarget);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        shader.setSampler("WeatherMapSampler", weatherTarget.getColorTextureId());
        shader.setSampler("MorphologyMapSampler", morphologyTarget.getColorTextureId());
        shader.setSampler("StormStructureMapSampler", stormStructureTarget.getColorTextureId());
        shader.setSampler("StormLayerHeightMapSampler", stormLayerHeightTarget.getColorTextureId());
        shader.setSampler("StormTowerMapSampler", stormTowerTarget.getColorTextureId());
        shader.setSampler("BlueNoiseSampler", CloudNoiseTextureManager.blueNoiseTextureId());
        SceneDepthFrame safeSceneDepth = sceneDepth == null ? SceneDepthFrame.INVALID : sceneDepth;
        shader.setSampler("SceneDepthSampler", safeSceneDepth.valid() ? safeSceneDepth.textureId() : 0);
        shader.setSampler("HistorySampler", historyValid ? historyTarget.getColorTextureId() : 0);
        shader.setSampler("HistoryDepthSampler", historyValid ? historyTarget.getDepthTextureId() : 0);

        shader.safeGetUniform("CloudProjMat").set(projection);
        shader.safeGetUniform("ViewRotMat").set(viewRotation);
        shader.safeGetUniform("InvProjMat").set(invProj);
        shader.safeGetUniform("InvViewRotMat").set(invViewRot);
        shader.safeGetUniform("PrevViewProjMat").set(prevViewProj);
        shader.safeGetUniform("CameraPos").set(cameraPos.x, cameraPos.y, cameraPos.z);
        shader.safeGetUniform("CameraCloudDensity").set(cameraCloudDensity);
        shader.safeGetUniform("WeatherOrigin").set((float) weather.originX(), (float) weather.originZ());
        shader.safeGetUniform("WeatherExtent").set(CloudWeatherMapRenderer.WEATHER_EXTENT);
        shader.safeGetUniform("SlabBaseY").set(weather.slabBaseY());
        shader.safeGetUniform("SlabTopY").set(weather.slabTopY());
        shader.safeGetUniform("MaxPrecipitation").set(weather.maxPrecipitation());
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
        shader.safeGetUniform("UseSceneDepth").set(sceneRayLimitEnabled && safeSceneDepth.valid() ? 1 : 0);
        shader.safeGetUniform("CoveragePretestEnabled").set(VolumetricCloudDebugConfig.coveragePretestEnabled() ? 1 : 0);
        shader.safeGetUniform("CoveragePretestSamples").set(VolumetricCloudDebugConfig.coveragePretestSamples());
        shader.safeGetUniform("CoveragePretestThreshold").set(VolumetricCloudDebugConfig.coveragePretestThreshold());
        shader.safeGetUniform("CoveragePretestDilation").set(VolumetricCloudDebugConfig.coveragePretestDilation());
        shader.safeGetUniform("HistoryValid").set(historyValid ? 1 : 0);
        shader.safeGetUniform("HistoryBlend").set(
                historyValid ? safeTuning.historyBlend() * historyConfidence : 0.0F
        );
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
        }

        prevProj.set(projection);
        prevViewRot.set(viewRotation);
        prevCameraPos.set(cameraPos);
        hasPrevFrame = true;
        frameIndex++;
        return true;
    }

    private static float rotationDelta(Matrix4f previous, Matrix4f current) {
        float max = 0.0F;
        max = Math.max(max, Math.abs(previous.m00() - current.m00()));
        max = Math.max(max, Math.abs(previous.m01() - current.m01()));
        max = Math.max(max, Math.abs(previous.m02() - current.m02()));
        max = Math.max(max, Math.abs(previous.m10() - current.m10()));
        max = Math.max(max, Math.abs(previous.m11() - current.m11()));
        max = Math.max(max, Math.abs(previous.m12() - current.m12()));
        max = Math.max(max, Math.abs(previous.m20() - current.m20()));
        max = Math.max(max, Math.abs(previous.m21() - current.m21()));
        return Math.max(max, Math.abs(previous.m22() - current.m22()));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0.0F, Math.min(1.0F, (value - edge0) / Math.max(0.000001F, edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
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
        bind3dSampler(program, "BaseNoiseSampler", 9, CloudNoiseTextureManager.baseTextureId());
        bind3dSampler(program, "DetailNoiseSampler", 10, CloudNoiseTextureManager.detailTextureId());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private static boolean hasTextureUnitCapacity() {
        if (fragmentTextureUnits < 0) {
            fragmentTextureUnits = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
            ProjectAtmosphere.LOGGER.info(
                    "[VolumetricClouds] fragment texture units available={} required=11",
                    fragmentTextureUnits
            );
            if (fragmentTextureUnits < 11) {
                ProjectAtmosphere.LOGGER.error(
                        "[VolumetricClouds] native renderer requires 11 fragment texture units but only {} are available",
                        fragmentTextureUnits
                );
            }
        }
        return fragmentTextureUnits >= 11;
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
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 9);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 10);
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

package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.render.CloudTextureUnitContract;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.Gabou.projectatmosphere.clouds.client.render.CloudGpuTimer;
import net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthFrame;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector2f;
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
    private static final int PUFF_CANDIDATE_TEXTURE_UNIT =
            CloudTextureUnitContract.PUFF_CANDIDATE_UNIT;
    private static final int BASE_NOISE_TEXTURE_UNIT = CloudTextureUnitContract.BASE_NOISE_UNIT;
    private static final int DETAIL_NOISE_TEXTURE_UNIT = CloudTextureUnitContract.DETAIL_NOISE_UNIT;
    private static final int REQUIRED_FRAGMENT_TEXTURE_UNITS =
            CloudTextureUnitContract.REQUIRED_FRAGMENT_TEXTURE_UNITS;
    private static final CloudGpuTimer GPU_TIMER = new CloudGpuTimer();
    private static final CloudFrameTimeGovernor GOVERNOR = new CloudFrameTimeGovernor();

    private static final Matrix4f prevProj = new Matrix4f();
    private static final Matrix4f prevViewRot = new Matrix4f();
    private static final Vector3f prevCameraPos = new Vector3f();
    private static final Vector2f prevMaterialOffset = new Vector2f();
    private static boolean hasPrevFrame;
    private static long frameIndex;
    private static volatile float lastGpuMilliseconds = -1.0F;
    private static volatile boolean lastHistoryValid;
    private static volatile float lastHistoryConfidence;
    private static volatile float lastResolutionScale = 1.0F;
    private static volatile long lastShaderFrameIndex = -1L;
    private static volatile LastDrawInputs lastDrawInputs = LastDrawInputs.EMPTY;
    private static boolean denseCameraResolution;
    private static int fragmentTextureUnits = -1;
    private static boolean renderedProductionFrame;
    private static VolumetricHistoryValidity.Key previousHistoryKey = VolumetricHistoryValidity.Key.EMPTY;
    private static volatile boolean historyResetBeforeNextComposite;

    private VolumetricCloudRenderer() {
    }

    public static float lastGpuMilliseconds() {
        return lastGpuMilliseconds;
    }

    /** Identifies a fresh completed GPU timestamp result without using a frame-time proxy. */
    static long lastGpuTimingSample() {
        return GPU_TIMER.getLastResultSerial();
    }

    public static float governorStepScale() {
        return GOVERNOR.stepScale();
    }

    /**
     * Test-only: holds the frame-time governor's step scale for T098 capture
     * sets, which are static poses whose purpose is to judge the renderer, not
     * the governor's load response.
     */
    public static void pinStepScaleForCaptures(float scale) {
        GOVERNOR.pin(scale);
    }

    /** Test-only: releases {@link #pinStepScaleForCaptures(float)}. */
    public static void releaseStepScalePin() {
        GOVERNOR.unpin();
    }

    public static float lastResolutionScale() {
        return lastResolutionScale;
    }

    public static void invalidateHistory() {
        historyResetBeforeNextComposite = false;
        hasPrevFrame = false;
        lastHistoryValid = false;
        VolumetricCloudRenderTargets.invalidateHistory();
        renderedProductionFrame = false;
        prevMaterialOffset.zero();
        previousHistoryKey = VolumetricHistoryValidity.Key.EMPTY;
    }

    /** Thread-safe lifecycle signal consumed before any next-frame history bind. */
    public static void invalidateBeforeNextComposite() {
        historyResetBeforeNextComposite = true;
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
        lastShaderFrameIndex = -1L;
        lastDrawInputs = LastDrawInputs.EMPTY;
        denseCameraResolution = false;
    }

    /** Whether the last raymarch consumed temporal history (for status/logs). */
    public static boolean lastHistoryValid() {
        return lastHistoryValid;
    }

    public static float lastHistoryConfidence() {
        return lastHistoryConfidence;
    }

    /** Exact FrameIndex value uploaded by the most recent successful pass. */
    public static long lastShaderFrameIndex() {
        return lastShaderFrameIndex;
    }

    /** Immutable values from the most recent successful shader upload. */
    public static LastDrawInputs lastDrawInputs() {
        return lastDrawInputs;
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
            VolumetricMaterialAdvectionTracker.Frame materialAdvection,
            VolumetricQualityProfile profile,
            float maxRenderDistance,
            FunnelUniforms funnels,
            Tuning tuning,
            boolean sceneRayLimitEnabled
    ) {
        if (historyResetBeforeNextComposite) {
            // Resource and backend lifecycle callbacks may originate away from
            // the render thread. Consume their signal before choosing history
            // samplers so no first frame can composite against an old key.
            invalidateHistory();
        }
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
        float diagnosticResolutionScale = VolumetricCloudDebugConfig.fixedResolutionScale();
        float resolutionScale = Float.isFinite(diagnosticResolutionScale)
                ? diagnosticResolutionScale
                : denseCameraResolution
                ? Math.min(profile.resolutionScale(), 0.50F)
                : profile.resolutionScale();
        lastResolutionScale = resolutionScale;
        if (!VolumetricCloudRenderTargets.prepareCloudTargets(mainTarget, resolutionScale)) {
            return false;
        }
        VolumetricHistoryValidity.Key currentHistoryKey = VolumetricHistoryValidity.Key.nativeFrame(
                VolumetricCloudClientLifecycle.worldGeneration(),
                VolumetricCloudClientLifecycle.dimensionGeneration(),
                VolumetricCloudClientLifecycle.ownerGeneration(),
                VolumetricCloudClientLifecycle.resourceGeneration(),
                StormGeometryBuildCoordinator.renderTopologyGeneration(),
                VolumetricCloudRenderTargets.resolutionGeneration()
        );
        if (hasPrevFrame
                && !VolumetricHistoryValidity.canRetain(previousHistoryKey, currentHistoryKey)) {
            // Descriptor interpolation/advection does not alter this key. Only
            // topology/lifecycle or an effective target-size transition drops
            // history, preventing a prior silhouette from ghosting the frame.
            invalidateHistory();
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
        float materialOffsetX = materialAdvection == null ? 0.0F : materialAdvection.offsetX();
        float materialOffsetZ = materialAdvection == null ? 0.0F : materialAdvection.offsetZ();
        float materialFrameDeltaX = hasPrevFrame ? materialOffsetX - prevMaterialOffset.x : 0.0F;
        float materialFrameDeltaZ = hasPrevFrame ? materialOffsetZ - prevMaterialOffset.y : 0.0F;

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
        RenderTarget cumulusStageSupportTarget =
                VolumetricCloudRenderTargets.prepareCumulusStageSupportTarget(profile.weatherMapSize());
        RenderTarget cumulusStageBaseTarget =
                VolumetricCloudRenderTargets.prepareCumulusStageBaseTarget(profile.weatherMapSize());
        RenderTarget cumulusStageTopTarget =
                VolumetricCloudRenderTargets.prepareCumulusStageTopTarget(profile.weatherMapSize());
        RenderTarget stormCandidateTarget = VolumetricCloudRenderTargets.prepareStormCandidateTarget();
        RenderTarget stormDescriptorTarget = VolumetricCloudRenderTargets.prepareStormDescriptorTarget();
        RenderTarget puffCandidateTarget = VolumetricCloudRenderTargets.preparePuffCandidateTarget();
        if (weatherTarget == null || morphologyTarget == null
                || cumulusStageSupportTarget == null || cumulusStageBaseTarget == null
                || cumulusStageTopTarget == null || stormCandidateTarget == null
                || stormDescriptorTarget == null || puffCandidateTarget == null) {
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
        shader.setSampler("CumulusStageSupportMapSampler", cumulusStageSupportTarget.getColorTextureId());
        shader.setSampler("CumulusStageBaseMapSampler", cumulusStageBaseTarget.getColorTextureId());
        shader.setSampler("CumulusStageTopMapSampler", cumulusStageTopTarget.getColorTextureId());
        shader.setSampler("StormCandidateMapSampler", stormCandidateTarget.getColorTextureId());
        shader.setSampler("StormDescriptorSampler", stormDescriptorTarget.getColorTextureId());
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
        shader.safeGetUniform("PuffLobeCount").set(PuffLobeSpatialIndex.lobeCount());
        shader.safeGetUniform("StormLobeCount").set(StormGeometryBuildCoordinator.lobeCount());
        shader.safeGetUniform("StormWidestEdgeBlocks")
                .set(StormGeometryBuildCoordinator.widestEdgeBlocks());
        shader.safeGetUniform("PuffShapeMode").set(PuffLobeSpatialIndex.effectiveShapeMode().shaderId());
        shader.safeGetUniform("PuffDensityStage").set(
                VolumetricCloudDebugConfig.puffDensityStage().shaderId()
        );
        shader.safeGetUniform("PuffTierFilter").set(
                VolumetricCloudDebugConfig.puffTierFilter().shaderId()
        );
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
        shader.safeGetUniform("MaterialOffset").set(materialOffsetX, materialOffsetZ);
        shader.safeGetUniform("MaterialFrameDelta").set(materialFrameDeltaX, materialFrameDeltaZ);
        // T132 Option B: a deterministic reference capture renders every frame
        // of its A/B comparison at one fixed clock, because WorldTime feeds the
        // precipitation shaft domain and would otherwise animate between passes.
        // The override lives in the diagnostic; the world clock is untouched and
        // every ordinary frame still uploads the live value.
        float liveWorldTimeTicks = worldTimeTicks;
        boolean worldTimePinned = StormReferenceImageCapture.worldTimePinned();
        float effectiveWorldTimeTicks =
                StormReferenceImageCapture.effectiveWorldTime(liveWorldTimeTicks);
        shader.safeGetUniform("WorldTime").set(effectiveWorldTimeTicks);
        long uploadedFrameIndex = frameIndex;
        float uploadedFrameIndexValue = (float) (uploadedFrameIndex % 1024L);
        shader.safeGetUniform("FrameIndex").set(uploadedFrameIndexValue);
        shader.safeGetUniform("RaymarchSteps").set(profile.raymarchSteps());
        shader.safeGetUniform("LightSteps").set(profile.lightSteps());
        shader.safeGetUniform("ScatterOctaves").set(profile.scatterOctaves());
        shader.safeGetUniform("DetailQuality").set(profile.detailQuality());
        shader.safeGetUniform("StepScale").set(stepScale);
        shader.safeGetUniform("ExteriorFineStep").set(
                PuffLobeSpatialIndex.exteriorFineStepWorld(profile.raymarchSteps(), stepScale)
        );
        float uploadedMaxRenderDistance = Math.max(300.0F, maxRenderDistance);
        boolean uploadedUseSceneDepth = sceneRayLimitEnabled && safeSceneDepth.valid();
        boolean uploadedCoveragePretestEnabled = VolumetricCloudDebugConfig.coveragePretestEnabled();
        int uploadedCoveragePretestSamples = VolumetricCloudDebugConfig.coveragePretestSamples();
        float uploadedCoveragePretestThreshold = VolumetricCloudDebugConfig.coveragePretestThreshold();
        int uploadedCoveragePretestDilation = VolumetricCloudDebugConfig.coveragePretestDilation();
        shader.safeGetUniform("MaxRenderDistance").set(uploadedMaxRenderDistance);
        shader.safeGetUniform("UseSceneDepth").set(uploadedUseSceneDepth ? 1 : 0);
        shader.safeGetUniform("CoveragePretestEnabled").set(uploadedCoveragePretestEnabled ? 1 : 0);
        shader.safeGetUniform("CoveragePretestSamples").set(uploadedCoveragePretestSamples);
        shader.safeGetUniform("CoveragePretestThreshold").set(uploadedCoveragePretestThreshold);
        shader.safeGetUniform("CoveragePretestDilation").set(uploadedCoveragePretestDilation);
        shader.safeGetUniform("HistoryValid").set(historyValid ? 1 : 0);
        float uploadedHistoryBlend = historyValid
                ? safeTuning.historyBlend() * historyConfidence
                : 0.0F;
        shader.safeGetUniform("HistoryBlend").set(uploadedHistoryBlend);
        VolumetricCloudRaymarchDebugView debugView = StormMaterialRuntimeTrace.active()
                ? VolumetricCloudRaymarchDebugView.STORM_MATERIAL_TRACE
                : StormWorkloadRuntimeCapture.active()
                    ? StormWorkloadRuntimeCapture.view()
                    : VolumetricCloudDebugConfig.raymarchDebugView();
        shader.safeGetUniform("DebugView").set(debugView.shaderId());
        shader.safeGetUniform("StormTopologyMode").set(
                VolumetricCloudDebugConfig.stormTopologyMode().shaderId()
        );
        // T133 / SC-020: zero outside a diagnostic capture, so ordinary frames
        // take the production optimized paths.
        shader.safeGetUniform("PaDiagnosticOptimizationMode").set(
                VolumetricCloudDebugConfig.optimizationDiagnosticMode().shaderFlags()
        );
        shader.safeGetUniform("StormTraceOrigin").set(
                StormMaterialRuntimeTrace.x(), StormMaterialRuntimeTrace.z()
        );
        shader.safeGetUniform("StormTraceYStart").set(StormMaterialRuntimeTrace.yStart());
        shader.safeGetUniform("StormTraceYInterval").set(StormMaterialRuntimeTrace.interval());
        shader.safeGetUniform("StormTraceSamples").set(StormMaterialRuntimeTrace.samples());
        shader.safeGetUniform("StormTraceStage").set(StormMaterialRuntimeTrace.stage());
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
        PuffLobeSpatialIndex.uploadDescriptors(shader.getId());
        bindManualTextures(shader, puffCandidateTarget.getColorTextureId());
        GPU_TIMER.begin();
        try {
            FullscreenQuad.draw(shader);
        } finally {
            GPU_TIMER.end();
            unbindManualTextures();
            shader.clear();
        }

        boolean worldTimeAffectsDensity = weather.maxPrecipitation() > 0.02F
                || safeFunnels.count() > 0;
        lastShaderFrameIndex = uploadedFrameIndex;
        lastDrawInputs = LastDrawInputs.capture(
                uploadedFrameIndex,
                uploadedFrameIndexValue,
                effectiveWorldTimeTicks,
                liveWorldTimeTicks,
                worldTimePinned,
                worldTimeAffectsDensity,
                projection,
                viewRotation,
                invProj,
                invViewRot,
                prevViewProj,
                cameraPos,
                cameraCloudDensity,
                weather,
                lighting,
                windVec,
                materialOffsetX,
                materialOffsetZ,
                materialFrameDeltaX,
                materialFrameDeltaZ,
                profile,
                stepScale,
                uploadedMaxRenderDistance,
                uploadedUseSceneDepth,
                uploadedCoveragePretestEnabled,
                uploadedCoveragePretestSamples,
                uploadedCoveragePretestThreshold,
                uploadedCoveragePretestDilation,
                historyValid,
                uploadedHistoryBlend,
                debugView,
                safeTuning,
                safeFunnels
        );

        renderedProductionFrame = debugView == VolumetricCloudRaymarchDebugView.FINAL;
        if (renderedProductionFrame) {
            prevProj.set(projection);
            prevViewRot.set(viewRotation);
            prevCameraPos.set(cameraPos);
            prevMaterialOffset.set(materialOffsetX, materialOffsetZ);
            previousHistoryKey = currentHistoryKey;
            hasPrevFrame = true;
        }
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
        if (renderedProductionFrame) {
            VolumetricCloudRenderTargets.swapAndMarkHistoryValid();
        }
        renderedProductionFrame = false;
    }

    public static void resetGovernor() {
        GOVERNOR.reset();
    }

    /**
     * Minecraft 1.20.1's managed shader path has twelve texture-state slots.
     * Storm candidates/descriptors stay managed; the PUFF candidate map is
     * bound manually on unit 12, followed by noise on units 13 and 14.
     */
    private static void bindManualTextures(ShaderInstance shader, int puffCandidateTextureId) {
        int program = shader.getId();
        bind2dSampler(
                program,
                "PuffCandidateMapSampler",
                PUFF_CANDIDATE_TEXTURE_UNIT,
                puffCandidateTextureId
        );
        bind3dSampler(
                program,
                "BaseNoiseSampler",
                BASE_NOISE_TEXTURE_UNIT,
                CloudNoiseTextureManager.baseTextureId()
        );
        bind3dSampler(
                program,
                "DetailNoiseSampler",
                DETAIL_NOISE_TEXTURE_UNIT,
                CloudNoiseTextureManager.detailTextureId()
        );
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private static boolean hasTextureUnitCapacity() {
        if (fragmentTextureUnits < 0) {
            fragmentTextureUnits = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
            ProjectAtmosphere.LOGGER.info(
                    "[VolumetricClouds] fragment texture units available={} required={}",
                    fragmentTextureUnits,
                    REQUIRED_FRAGMENT_TEXTURE_UNITS
            );
            if (fragmentTextureUnits < REQUIRED_FRAGMENT_TEXTURE_UNITS) {
                ProjectAtmosphere.LOGGER.error(
                        "[VolumetricClouds] native renderer requires {} fragment texture units but only {} are available",
                        REQUIRED_FRAGMENT_TEXTURE_UNITS,
                        fragmentTextureUnits
                );
            }
        }
        return fragmentTextureUnits >= REQUIRED_FRAGMENT_TEXTURE_UNITS;
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

    private static void unbindManualTextures() {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + PUFF_CANDIDATE_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + BASE_NOISE_TEXTURE_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + DETAIL_NOISE_TEXTURE_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private static void bind2dSampler(int program, String name, int unit, int textureId) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0 || textureId <= 0) {
            return;
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL20.glUniform1i(location, unit);
    }

    /**
     * Immutable snapshot of the scalar/vector/matrix values uploaded by the
     * latest successful volume draw. The comparison signature excludes
     * FrameIndex, inactive previous-frame inputs while history is disabled and,
     * when no shader branch can consume it, WorldTime. The observation
     * signature additionally excludes WorldTime so an
     * explicitly labelled, non-controlled temporal delta can still be measured
     * without weakening the strict comparison contract. Sampler contents are
     * not part of these scalar signatures; reports explicitly identify the
     * changing history colour/depth textures as uncontrolled.
     */
    public record LastDrawInputs(
            boolean valid,
            long uniformSignature,
            long comparisonUniformSignature,
            long observationUniformSignature,
            UniformComponentSignatures comparisonUniformComponents,
            UniformComponentSignatures observationUniformComponents,
            long frameIndex,
            float frameIndexValue,
            /** The clock actually uploaded to the shader for this draw. */
            float worldTimeTicks,
            /** The live world clock, retained for auditability while pinned. */
            float liveWorldTimeTicks,
            boolean worldTimePinned,
            boolean worldTimeAffectsDensity,
            float cameraCloudDensity,
            float materialOffsetX,
            float materialOffsetZ,
            float materialFrameDeltaX,
            float materialFrameDeltaZ,
            float windX,
            float windY,
            float windZ,
            float maxPrecipitation,
            float maxRenderDistance,
            boolean useSceneDepth,
            boolean coveragePretestEnabled,
            int coveragePretestSamples,
            float coveragePretestThreshold,
            int coveragePretestDilation,
            int raymarchSteps,
            int lightSteps,
            int scatterOctaves,
            int detailQuality,
            float stepScale,
            boolean historyValid,
            float historyBlend,
            VolumetricCloudRaymarchDebugView debugView,
            StormTopologyMode stormTopologyMode,
            /** T133: the optimization mode this frame was actually drawn with. */
            StormOptimizationDiagnosticMode optimizationDiagnosticMode,
            float densityMul,
            float coverageMul,
            float extinctionScale,
            int funnelCount,
            long funnelSignature,
            // T132 attribution only. These mirror the LightDir uniform already
            // uploaded to the production shader; they are appended after every
            // signature field so no existing uniform signature changes.
            float lightDirX,
            float lightDirY,
            float lightDirZ
    ) {
        private static final long FNV_OFFSET = 0xcbf29ce484222325L;
        private static final long FNV_PRIME = 0x100000001b3L;
        public static final LastDrawInputs EMPTY = new LastDrawInputs(
                false, 0L, 0L, 0L,
                UniformComponentSignatures.EMPTY,
                UniformComponentSignatures.EMPTY,
                -1L,
                0.0F, 0.0F, 0.0F, false, false,
                0.0F,
                0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F,
                false,
                false, 0, 0.0F, 0,
                0, 0, 0, 0, 0.0F,
                false, 0.0F,
                VolumetricCloudRaymarchDebugView.FINAL,
                StormTopologyMode.COMPACT,
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION,
                0.0F, 0.0F, 0.0F,
                0, 0L,
                0.0F, 0.0F, 0.0F
        );

        private static LastDrawInputs capture(
                long frameIndex,
                float frameIndexValue,
                float worldTimeTicks,
                float liveWorldTimeTicks,
                boolean worldTimePinned,
                boolean worldTimeAffectsDensity,
                Matrix4f projection,
                Matrix4f viewRotation,
                Matrix4f inverseProjection,
                Matrix4f inverseViewRotation,
                Matrix4f previousViewProjection,
                Vector3f cameraPosition,
                float cameraCloudDensity,
                CloudWeatherMapRenderer.Result weather,
                VolumetricCloudLighting.Frame lighting,
                Vector3f wind,
                float materialOffsetX,
                float materialOffsetZ,
                float materialFrameDeltaX,
                float materialFrameDeltaZ,
                VolumetricQualityProfile profile,
                float stepScale,
                float maxRenderDistance,
                boolean useSceneDepth,
                boolean coveragePretestEnabled,
                int coveragePretestSamples,
                float coveragePretestThreshold,
                int coveragePretestDilation,
                boolean historyValid,
                float historyBlend,
                VolumetricCloudRaymarchDebugView debugView,
                Tuning tuning,
                FunnelUniforms funnels
        ) {
            long funnelSignature = funnelSignature(funnels);
            boolean captureComponents =
                    VolumetricStabilityDiagnostics.captureUniformComponentsForNextFrame()
                            // T132: a deterministic reference frame retains the
                            // same named component hashes so PASS A and PASS B
                            // can be diffed component-by-component.
                            || StormReferenceImageCapture.active();
            UniformComponentSignatures comparisonComponents = captureComponents
                    ? UniformComponentSignatures.capture(
                            projection, viewRotation, inverseProjection, inverseViewRotation,
                            previousViewProjection, cameraPosition, cameraCloudDensity, weather,
                            lighting, wind, materialOffsetX, materialOffsetZ,
                            materialFrameDeltaX, materialFrameDeltaZ, worldTimeTicks,
                            profile, stepScale, maxRenderDistance, useSceneDepth,
                            coveragePretestEnabled, coveragePretestSamples,
                            coveragePretestThreshold, coveragePretestDilation, historyValid,
                            historyBlend, debugView, tuning, funnels,
                            worldTimeAffectsDensity, false
                    )
                    : UniformComponentSignatures.EMPTY;
            UniformComponentSignatures observationComponents = captureComponents
                    ? UniformComponentSignatures.capture(
                            projection, viewRotation, inverseProjection, inverseViewRotation,
                            previousViewProjection, cameraPosition, cameraCloudDensity, weather,
                            lighting, wind, materialOffsetX, materialOffsetZ,
                            materialFrameDeltaX, materialFrameDeltaZ, worldTimeTicks,
                            profile, stepScale, maxRenderDistance, useSceneDepth,
                            coveragePretestEnabled, coveragePretestSamples,
                            coveragePretestThreshold, coveragePretestDilation, historyValid,
                            historyBlend, debugView, tuning, funnels,
                            false, false
                    )
                    : UniformComponentSignatures.EMPTY;
            long exact = uniformSignature(
                    projection, viewRotation, inverseProjection, inverseViewRotation,
                    previousViewProjection, cameraPosition, cameraCloudDensity, weather,
                    lighting, wind, materialOffsetX, materialOffsetZ,
                    materialFrameDeltaX, materialFrameDeltaZ, worldTimeTicks,
                    frameIndexValue, profile, stepScale, maxRenderDistance,
                    useSceneDepth, coveragePretestEnabled, coveragePretestSamples,
                    coveragePretestThreshold, coveragePretestDilation, historyValid,
                    historyBlend, debugView, tuning, funnels, true, true, true
            );
            long comparable = uniformSignature(
                    projection, viewRotation, inverseProjection, inverseViewRotation,
                    previousViewProjection, cameraPosition, cameraCloudDensity, weather,
                    lighting, wind, materialOffsetX, materialOffsetZ,
                    materialFrameDeltaX, materialFrameDeltaZ, worldTimeTicks,
                    frameIndexValue, profile, stepScale, maxRenderDistance,
                    useSceneDepth, coveragePretestEnabled, coveragePretestSamples,
                    coveragePretestThreshold, coveragePretestDilation, historyValid,
                    historyBlend, debugView, tuning, funnels,
                    false, worldTimeAffectsDensity, false
            );
            long observed = uniformSignature(
                    projection, viewRotation, inverseProjection, inverseViewRotation,
                    previousViewProjection, cameraPosition, cameraCloudDensity, weather,
                    lighting, wind, materialOffsetX, materialOffsetZ,
                    materialFrameDeltaX, materialFrameDeltaZ, worldTimeTicks,
                    frameIndexValue, profile, stepScale, maxRenderDistance,
                    useSceneDepth, coveragePretestEnabled, coveragePretestSamples,
                    coveragePretestThreshold, coveragePretestDilation, historyValid,
                    historyBlend, debugView, tuning, funnels,
                    false, false, false
            );
            return new LastDrawInputs(
                    true,
                    exact,
                    comparable,
                    observed,
                    comparisonComponents,
                    observationComponents,
                    frameIndex,
                    frameIndexValue,
                    worldTimeTicks,
                    liveWorldTimeTicks,
                    worldTimePinned,
                    worldTimeAffectsDensity,
                    cameraCloudDensity,
                    materialOffsetX,
                    materialOffsetZ,
                    materialFrameDeltaX,
                    materialFrameDeltaZ,
                    wind.x,
                    wind.y,
                    wind.z,
                    weather.maxPrecipitation(),
                    maxRenderDistance,
                    useSceneDepth,
                    coveragePretestEnabled,
                    coveragePretestSamples,
                    coveragePretestThreshold,
                    coveragePretestDilation,
                    profile.raymarchSteps(),
                    profile.lightSteps(),
                    profile.scatterOctaves(),
                    profile.detailQuality(),
                    stepScale,
                    historyValid,
                    historyBlend,
                    debugView,
                    VolumetricCloudDebugConfig.stormTopologyMode(),
                    VolumetricCloudDebugConfig.optimizationDiagnosticMode(),
                    tuning.densityMul(),
                    tuning.coverageMul(),
                    tuning.extinctionScale(),
                    funnels.count(),
                    funnelSignature,
                    lighting.lightDirection().x,
                    lighting.lightDirection().y,
                    lighting.lightDirection().z
            );
        }

        private static long uniformSignature(
                Matrix4f projection,
                Matrix4f viewRotation,
                Matrix4f inverseProjection,
                Matrix4f inverseViewRotation,
                Matrix4f previousViewProjection,
                Vector3f cameraPosition,
                float cameraCloudDensity,
                CloudWeatherMapRenderer.Result weather,
                VolumetricCloudLighting.Frame lighting,
                Vector3f wind,
                float materialOffsetX,
                float materialOffsetZ,
                float materialFrameDeltaX,
                float materialFrameDeltaZ,
                float worldTimeTicks,
                float frameIndexValue,
                VolumetricQualityProfile profile,
                float stepScale,
                float maxRenderDistance,
                boolean useSceneDepth,
                boolean coveragePretestEnabled,
                int coveragePretestSamples,
                float coveragePretestThreshold,
                int coveragePretestDilation,
                boolean historyValid,
                float historyBlend,
                VolumetricCloudRaymarchDebugView debugView,
                Tuning tuning,
                FunnelUniforms funnels,
                boolean includeFrameIndex,
                boolean includeWorldTime,
                boolean includeInactiveTemporal
        ) {
            long hash = FNV_OFFSET;
            hash = mixMatrix(hash, projection);
            hash = mixMatrix(hash, viewRotation);
            hash = mixMatrix(hash, inverseProjection);
            hash = mixMatrix(hash, inverseViewRotation);
            boolean temporalInputsActive = includeInactiveTemporal || historyValid;
            if (temporalInputsActive) {
                hash = mix(hash, temporalMatrixSignature(previousViewProjection, true));
            }
            hash = mixVector(hash, cameraPosition);
            hash = mixFloat(hash, cameraCloudDensity);
            hash = mixFloat(hash, (float) weather.originX());
            hash = mixFloat(hash, (float) weather.originZ());
            hash = mixFloat(hash, CloudWeatherMapRenderer.WEATHER_EXTENT);
            hash = mixFloat(hash, weather.slabBaseY());
            hash = mixFloat(hash, weather.slabTopY());
            hash = mixFloat(hash, weather.maxPrecipitation());
            hash = mix(hash, PuffLobeSpatialIndex.descriptorSignatureForDiagnostics());
            hash = mixVector(hash, lighting.lightDirection());
            hash = mixVector(hash, lighting.lightColor());
            hash = mixVector(hash, lighting.ambientTop());
            hash = mixVector(hash, lighting.ambientBottom());
            hash = mixFloat(hash, lighting.sunsetStrength());
            hash = mixFloat(hash, lighting.nightFactor());
            hash = mixFloat(hash, lighting.stormDarkening());
            hash = mixVector(hash, wind);
            hash = mixFloat(hash, materialOffsetX);
            hash = mixFloat(hash, materialOffsetZ);
            if (temporalInputsActive) {
                hash = mix(hash, temporalPairSignature(
                        materialFrameDeltaX,
                        materialFrameDeltaZ,
                        true
                ));
            }
            if (includeWorldTime) {
                hash = mixFloat(hash, worldTimeTicks);
            }
            if (includeFrameIndex) {
                hash = mixFloat(hash, frameIndexValue);
            }
            hash = mix(hash, profile.raymarchSteps());
            hash = mix(hash, profile.lightSteps());
            hash = mix(hash, profile.scatterOctaves());
            hash = mix(hash, profile.detailQuality());
            hash = mixFloat(hash, stepScale);
            hash = mixFloat(hash, maxRenderDistance);
            hash = mix(hash, useSceneDepth ? 1L : 0L);
            hash = mix(hash, coveragePretestEnabled ? 1L : 0L);
            hash = mix(hash, coveragePretestSamples);
            hash = mixFloat(hash, coveragePretestThreshold);
            hash = mix(hash, coveragePretestDilation);
            hash = mix(hash, historyValid ? 1L : 0L);
            hash = mixFloat(hash, historyBlend);
            hash = mix(hash, debugView.shaderId());
            hash = mixFloat(hash, tuning.densityMul());
            hash = mixFloat(hash, tuning.coverageMul());
            hash = mixFloat(hash, tuning.extinctionScale());
            hash = mix(hash, funnels.count());
            return mix(hash, funnelSignature(funnels));
        }

        /**
         * Component-level signatures retained only for an explicitly requested
         * stability capture. Normal rendering receives {@link #EMPTY}, avoiding
         * per-frame diagnostic allocations while still making every rejected
         * pair attributable to a concrete uploaded-input group.
         */
        public record UniformComponentSignatures(
                long projection,
                long viewRotation,
                long inverseProjection,
                long inverseViewRotation,
                long previousViewProjection,
                long cameraPosition,
                int cameraCloudDensityBits,
                long weatherUniforms,
                long lightDirection,
                long lightColor,
                long ambientTop,
                long ambientBottom,
                int sunsetStrengthBits,
                int nightFactorBits,
                int stormDarkeningBits,
                long wind,
                long materialOffset,
                long materialFrameDelta,
                long qualityFlags,
                long funnels
        ) {
            public static final UniformComponentSignatures EMPTY =
                    new UniformComponentSignatures(
                            0L, 0L, 0L, 0L, 0L,
                            0L, 0, 0L,
                            0L, 0L, 0L, 0L,
                            0, 0, 0,
                            0L, 0L, 0L, 0L, 0L
                    );

            private static UniformComponentSignatures capture(
                    Matrix4f projection,
                    Matrix4f viewRotation,
                    Matrix4f inverseProjection,
                    Matrix4f inverseViewRotation,
                    Matrix4f previousViewProjection,
                    Vector3f cameraPosition,
                    float cameraCloudDensity,
                    CloudWeatherMapRenderer.Result weather,
                    VolumetricCloudLighting.Frame lighting,
                    Vector3f wind,
                    float materialOffsetX,
                    float materialOffsetZ,
                    float materialFrameDeltaX,
                    float materialFrameDeltaZ,
                    float worldTimeTicks,
                    VolumetricQualityProfile profile,
                    float stepScale,
                    float maxRenderDistance,
                    boolean useSceneDepth,
                    boolean coveragePretestEnabled,
                    int coveragePretestSamples,
                    float coveragePretestThreshold,
                    int coveragePretestDilation,
                    boolean historyValid,
                    float historyBlend,
                    VolumetricCloudRaymarchDebugView debugView,
                    Tuning tuning,
                    FunnelUniforms funnels,
                    boolean includeWorldTime,
                    boolean includeInactiveTemporal
            ) {
                long weatherHash = FNV_OFFSET;
                weatherHash = mixFloat(weatherHash, (float) weather.originX());
                weatherHash = mixFloat(weatherHash, (float) weather.originZ());
                weatherHash = mixFloat(weatherHash, CloudWeatherMapRenderer.WEATHER_EXTENT);
                weatherHash = mixFloat(weatherHash, weather.slabBaseY());
                weatherHash = mixFloat(weatherHash, weather.slabTopY());
                weatherHash = mixFloat(weatherHash, weather.maxPrecipitation());
                if (includeWorldTime) {
                    weatherHash = mixFloat(weatherHash, worldTimeTicks);
                }

                long qualityHash = FNV_OFFSET;
                qualityHash = mix(qualityHash, profile.raymarchSteps());
                qualityHash = mix(qualityHash, profile.lightSteps());
                qualityHash = mix(qualityHash, profile.scatterOctaves());
                qualityHash = mix(qualityHash, profile.detailQuality());
                qualityHash = mixFloat(qualityHash, stepScale);
                qualityHash = mixFloat(qualityHash, maxRenderDistance);
                qualityHash = mix(qualityHash, useSceneDepth ? 1L : 0L);
                qualityHash = mix(qualityHash, coveragePretestEnabled ? 1L : 0L);
                qualityHash = mix(qualityHash, coveragePretestSamples);
                qualityHash = mixFloat(qualityHash, coveragePretestThreshold);
                qualityHash = mix(qualityHash, coveragePretestDilation);
                qualityHash = mix(qualityHash, historyValid ? 1L : 0L);
                qualityHash = mixFloat(qualityHash, historyBlend);
                qualityHash = mix(qualityHash, debugView.shaderId());
                qualityHash = mixFloat(qualityHash, tuning.densityMul());
                qualityHash = mixFloat(qualityHash, tuning.coverageMul());
                qualityHash = mixFloat(qualityHash, tuning.extinctionScale());

                boolean temporalInputsActive = includeInactiveTemporal || historyValid;
                return new UniformComponentSignatures(
                        mixMatrix(FNV_OFFSET, projection),
                        mixMatrix(FNV_OFFSET, viewRotation),
                        mixMatrix(FNV_OFFSET, inverseProjection),
                        mixMatrix(FNV_OFFSET, inverseViewRotation),
                        temporalMatrixSignature(previousViewProjection, temporalInputsActive),
                        mixVector(FNV_OFFSET, cameraPosition),
                        Float.floatToRawIntBits(cameraCloudDensity),
                        weatherHash,
                        mixVector(FNV_OFFSET, lighting.lightDirection()),
                        mixVector(FNV_OFFSET, lighting.lightColor()),
                        mixVector(FNV_OFFSET, lighting.ambientTop()),
                        mixVector(FNV_OFFSET, lighting.ambientBottom()),
                        Float.floatToRawIntBits(lighting.sunsetStrength()),
                        Float.floatToRawIntBits(lighting.nightFactor()),
                        Float.floatToRawIntBits(lighting.stormDarkening()),
                        mixVector(FNV_OFFSET, wind),
                        mixPair(materialOffsetX, materialOffsetZ),
                        temporalPairSignature(
                                materialFrameDeltaX,
                                materialFrameDeltaZ,
                                temporalInputsActive
                        ),
                        qualityHash,
                        funnelSignature(funnels)
                );
            }

            public String compact() {
                long currentMatrices = FNV_OFFSET;
                currentMatrices = mix(currentMatrices, projection);
                currentMatrices = mix(currentMatrices, viewRotation);
                currentMatrices = mix(currentMatrices, inverseProjection);
                currentMatrices = mix(currentMatrices, inverseViewRotation);
                long camera = mix(mix(FNV_OFFSET, cameraPosition), cameraCloudDensityBits);
                long lighting = FNV_OFFSET;
                lighting = mix(lighting, lightDirection);
                lighting = mix(lighting, lightColor);
                lighting = mix(lighting, ambientTop);
                lighting = mix(lighting, ambientBottom);
                lighting = mix(lighting, sunsetStrengthBits);
                lighting = mix(lighting, nightFactorBits);
                lighting = mix(lighting, stormDarkeningBits);
                long advection = FNV_OFFSET;
                advection = mix(advection, wind);
                advection = mix(advection, materialOffset);
                advection = mix(advection, materialFrameDelta);
                return "matrices=" + hex(currentMatrices)
                        + " prevVP=" + hex(previousViewProjection)
                        + " camera=" + hex(camera)
                        + " weatherUniforms=" + hex(weatherUniforms)
                        + " lighting=" + hex(lighting)
                        + " advection=" + hex(advection)
                        + " quality=" + hex(qualityFlags)
                        + " funnels=" + hex(funnels);
            }

            public String changesFrom(UniformComponentSignatures previous) {
                if (previous == null || previous == EMPTY) {
                    return "unavailable";
                }
                StringBuilder changes = new StringBuilder();
                appendChange(changes, "Projection", previous.projection, projection);
                appendChange(changes, "ViewRotation", previous.viewRotation, viewRotation);
                appendChange(changes, "InverseProjection", previous.inverseProjection, inverseProjection);
                appendChange(changes, "InverseViewRotation", previous.inverseViewRotation, inverseViewRotation);
                appendChange(changes, "PrevViewProjection",
                        previous.previousViewProjection, previousViewProjection);
                appendChange(changes, "CameraPosition", previous.cameraPosition, cameraPosition);
                appendChange(changes, "CameraCloudDensity",
                        Integer.toUnsignedLong(previous.cameraCloudDensityBits),
                        Integer.toUnsignedLong(cameraCloudDensityBits));
                appendChange(changes, "WeatherUniforms", previous.weatherUniforms, weatherUniforms);
                appendChange(changes, "LightDirection", previous.lightDirection, lightDirection);
                appendChange(changes, "LightColor", previous.lightColor, lightColor);
                appendChange(changes, "AmbientTop", previous.ambientTop, ambientTop);
                appendChange(changes, "AmbientBottom", previous.ambientBottom, ambientBottom);
                appendChange(changes, "SunsetStrength",
                        Integer.toUnsignedLong(previous.sunsetStrengthBits),
                        Integer.toUnsignedLong(sunsetStrengthBits));
                appendChange(changes, "NightFactor",
                        Integer.toUnsignedLong(previous.nightFactorBits),
                        Integer.toUnsignedLong(nightFactorBits));
                appendChange(changes, "StormDarkening",
                        Integer.toUnsignedLong(previous.stormDarkeningBits),
                        Integer.toUnsignedLong(stormDarkeningBits));
                appendChange(changes, "Wind", previous.wind, wind);
                appendChange(changes, "MaterialOffset", previous.materialOffset, materialOffset);
                appendChange(changes, "MaterialFrameDelta",
                        previous.materialFrameDelta, materialFrameDelta);
                appendChange(changes, "QualityFlags", previous.qualityFlags, qualityFlags);
                appendChange(changes, "Funnels", previous.funnels, funnels);
                return changes.length() == 0 ? "none" : changes.toString();
            }

            private static long mixPair(float first, float second) {
                return mixFloat(mixFloat(FNV_OFFSET, first), second);
            }

            private static void appendChange(
                    StringBuilder builder,
                    String name,
                    long previous,
                    long current
            ) {
                if (previous == current) {
                    return;
                }
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(name)
                        .append('=')
                        .append(hex(previous))
                        .append("->")
                        .append(hex(current));
            }

            private static String hex(long value) {
                return String.format(java.util.Locale.ROOT, "%016x", value);
            }
        }

        static void selfCheckUniformComponentBranching() {
            Matrix4f firstMatrix = new Matrix4f();
            Matrix4f changedMatrix = new Matrix4f().translate(3.0F, -2.0F, 1.0F);
            if (temporalMatrixSignature(firstMatrix, false) != 0L
                    || temporalMatrixSignature(changedMatrix, false) != 0L
                    || temporalPairSignature(0.0F, 0.0F, false) != 0L
                    || temporalPairSignature(4.0F, -3.0F, false) != 0L) {
                throw new IllegalStateException("inactive history inputs affected effective signatures");
            }
            if (temporalMatrixSignature(firstMatrix, true)
                    == temporalMatrixSignature(changedMatrix, true)
                    || temporalPairSignature(0.0F, 0.0F, true)
                    == temporalPairSignature(4.0F, -3.0F, true)) {
                throw new IllegalStateException("active history inputs were absent from effective signatures");
            }
        }

        private static long temporalMatrixSignature(Matrix4f matrix, boolean active) {
            return active ? mixMatrix(FNV_OFFSET, matrix) : 0L;
        }

        private static long temporalPairSignature(float first, float second, boolean active) {
            return active ? mixFloat(mixFloat(FNV_OFFSET, first), second) : 0L;
        }

        private static long funnelSignature(FunnelUniforms funnels) {
            long hash = mix(FNV_OFFSET, funnels.count());
            hash = mixArray(hash, funnels.f0a());
            hash = mixArray(hash, funnels.f0b());
            hash = mixArray(hash, funnels.f1a());
            return mixArray(hash, funnels.f1b());
        }

        private static long mixMatrix(long hash, Matrix4f matrix) {
            hash = mixFloat(hash, matrix.m00());
            hash = mixFloat(hash, matrix.m01());
            hash = mixFloat(hash, matrix.m02());
            hash = mixFloat(hash, matrix.m03());
            hash = mixFloat(hash, matrix.m10());
            hash = mixFloat(hash, matrix.m11());
            hash = mixFloat(hash, matrix.m12());
            hash = mixFloat(hash, matrix.m13());
            hash = mixFloat(hash, matrix.m20());
            hash = mixFloat(hash, matrix.m21());
            hash = mixFloat(hash, matrix.m22());
            hash = mixFloat(hash, matrix.m23());
            hash = mixFloat(hash, matrix.m30());
            hash = mixFloat(hash, matrix.m31());
            hash = mixFloat(hash, matrix.m32());
            return mixFloat(hash, matrix.m33());
        }

        private static long mixVector(long hash, Vector3f vector) {
            hash = mixFloat(hash, vector.x);
            hash = mixFloat(hash, vector.y);
            return mixFloat(hash, vector.z);
        }

        private static long mixArray(long hash, float[] values) {
            if (values == null) {
                return mix(hash, -1L);
            }
            hash = mix(hash, values.length);
            for (float value : values) {
                hash = mixFloat(hash, value);
            }
            return hash;
        }

        private static long mixFloat(long hash, float value) {
            return mix(hash, Float.floatToIntBits(value));
        }

        private static long mix(long hash, long value) {
            hash ^= value;
            return hash * FNV_PRIME;
        }
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

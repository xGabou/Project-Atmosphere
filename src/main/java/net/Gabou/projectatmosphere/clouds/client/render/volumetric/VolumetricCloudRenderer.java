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
    private static final int RAIN_FIELD_TEXTURE_UNIT = CloudTextureUnitContract.RAIN_FIELD_UNIT;
    private static final int STORM_FIELD_TEXTURE_UNIT = CloudTextureUnitContract.STORM_FIELD_UNIT;
    private static final int RAIN_PASS_TEXTURE_UNIT = CloudTextureUnitContract.RAIN_PASS_UNIT;
    /** T205. The rain pass's texture for the frame being drawn, or 0. */
    private static int rainPassTextureId;
    /** T210. The rain target the resolve/composite reads this frame (the lattice one when it ran). */
    private static RenderTarget lastRainCompositeTarget;
    private static final CloudGpuTimer RAIN_LATTICE_TIMER = new CloudGpuTimer();
    private static volatile float lastRainLatticeGpuMilliseconds = -1.0F;
    /**
     * T188. The field texture for the frame being drawn, or 0. Held here
     * because the manual bind has to happen after every shader.apply(),
     * which resets the sampler uniforms Minecraft knows about.
     */
    private static int rainFieldTextureId;
    /** T196. The storm field atlas; T203 gave it its own unit beside the rain gate. */
    private static int stormFieldTextureId;
    private static final int REQUIRED_FRAGMENT_TEXTURE_UNITS =
            CloudTextureUnitContract.REQUIRED_FRAGMENT_TEXTURE_UNITS;
    private static final CloudGpuTimer GPU_TIMER = new CloudGpuTimer();
    /**
     * T188. The rain-field generation pass is timed separately from the march,
     * because the decision the field has to win is build + lookup against the
     * old repeated traversal - and a cloud-ray saving on its own would hide
     * exactly the half of that trade the field pays for.
     */
    private static final CloudGpuTimer RAIN_FIELD_TIMER = new CloudGpuTimer();
    /**
     * T203. The rain gate field's build, on its own clock: the storm field
     * and the gate build in the same frame, and one timer holds one result.
     * The campaign's field column carries both builds (the net accounting
     * charges the arm its gate build) and the profile records this one
     * beside it so the gate's own cost is read directly.
     */
    private static final CloudGpuTimer RAIN_GATE_TIMER = new CloudGpuTimer();
    /** T205. The rain pass on its own clock. */
    private static final CloudGpuTimer RAIN_PASS_TIMER = new CloudGpuTimer();
    private static final CloudFrameTimeGovernor GOVERNOR = new CloudFrameTimeGovernor();

    private static final Matrix4f prevProj = new Matrix4f();
    private static final Matrix4f prevViewRot = new Matrix4f();
    private static final Vector3f prevCameraPos = new Vector3f();
    private static final Vector2f prevMaterialOffset = new Vector2f();
    private static boolean hasPrevFrame;
    private static long frameIndex;
    private static volatile float lastGpuMilliseconds = -1.0F;
    private static volatile float lastRainFieldGpuMilliseconds = -1.0F;
    private static volatile float lastRainGateGpuMilliseconds = -1.0F;
    private static boolean lastRainLatticeRan;
    private static volatile float lastRainPassGpuMilliseconds = -1.0F;
    /**
     * T199e. The field rebuilds every frame; these only count how often it
     * need not have. A build's inputs are the descriptor set and candidate
     * grid, the weather rasters' generation, the weather origin and slab, and
     * the material advection offset; when none of them moved since the last
     * build the previous atlas was exact. Reuse is deliberately not
     * implemented here - T196 Task 11's moving-fixture measurement was never
     * taken - so a future task starts from these numbers, not from a hunch.
     */
    private static long stormFieldInputSignature;
    private static long lastStormFieldDescriptorSignature;
    private static boolean stormFieldInputSignatureValid;
    private static long stormFieldBuilds;
    private static long stormFieldBuildsInputsUnchanged;
    private static long stormFieldBuildsDescriptorsUnchanged;
    private static volatile boolean lastHistoryValid;
    private static volatile float lastHistoryConfidence;
    private static volatile float lastResolutionScale = 1.0F;
    private static volatile long lastShaderFrameIndex = -1L;
    /** T224. The ray volume's floor, slab base and precipitation of the last draw. */
    private static volatile float lastRayVolumeFloorY = Float.NaN;
    private static volatile float lastSlabBaseY = Float.NaN;
    private static volatile float lastSlabTopY = Float.NaN;
    /** T225. The weather map's own maximum, before any diagnostic override. */
    private static volatile float lastNaturalMaxPrecipitation = Float.NaN;
    private static volatile float lastMaxPrecipitation = Float.NaN;
    private static volatile LastDrawInputs lastDrawInputs = LastDrawInputs.EMPTY;
    private static boolean denseCameraResolution;
    private static int fragmentTextureUnits = -1;
    private static boolean renderedProductionFrame;
    private static volatile CoreCostDiagnosticProgram lastProgram =
            CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH;
    /**
     * The exact transform the last draw uploaded. Diagnostics that must
     * address a specific rendered pixel project through this rather than
     * rebuilding a camera matrix that might not match the shader's.
     */
    private static final Matrix4f lastCloudProjection = new Matrix4f();
    private static final Matrix4f lastViewRotation = new Matrix4f();
    private static final Vector3f lastCameraPosition = new Vector3f();
    private static boolean lastTransformValid;
    private static VolumetricHistoryValidity.Key previousHistoryKey = VolumetricHistoryValidity.Key.EMPTY;
    private static volatile boolean historyResetBeforeNextComposite;
    /**
     * T207. True while the last march wrote a raw temporal phase; the hook
     * then resolves the four phases before the composite. The matrices of
     * that frame are kept for the resolve.
     */
    private static volatile boolean temporalPhaseActive;
    private static final Matrix4f lastInvProj = new Matrix4f();
    private static final Matrix4f lastInvViewRot = new Matrix4f();
    private static final Matrix4f lastViewProj = new Matrix4f();
    private static float lastMaxRenderDistance = 2000.0F;

    private VolumetricCloudRenderer() {
    }

    public static float lastGpuMilliseconds() {
        return lastGpuMilliseconds;
    }

    /**
     * T224. The floor of the ray volume: SlabBaseY less the precipitation ray
     * padding. A camera between it and SlabTopY starts inside
     * (cameraStartsInsideSlab), which still gates the in-cloud light probe and
     * the od-28 cone early-out; it no longer shortens the light cone.
     */
    public static float lastRayVolumeFloorY() {
        return lastRayVolumeFloorY;
    }

    public static float lastSlabBaseY() {
        return lastSlabBaseY;
    }

    public static float lastSlabTopY() {
        return lastSlabTopY;
    }

    public static float lastNaturalMaxPrecipitation() {
        return lastNaturalMaxPrecipitation;
    }

    public static float lastMaxPrecipitation() {
        return lastMaxPrecipitation;
    }

    /**
     * T188. Milliseconds the rain-support field generation pass cost on the
     * GPU, or -1 when no program generated one this frame.
     */
    public static float lastRainFieldGpuMilliseconds() {
        return lastRainFieldGpuMilliseconds;
    }

    /**
     * T203. Milliseconds the rain gate field's build cost on the GPU, or -1
     * when no program built one this frame. Already included in
     * {@link #lastRainFieldGpuMilliseconds()} for a frame that built both.
     */
    public static float lastRainGateGpuMilliseconds() {
        return lastRainGateGpuMilliseconds;
    }

    /** T210. Milliseconds the lattice rain pass cost, or 0 when the frame drew none. */
    public static float lastRainLatticeGpuMilliseconds() {
        return lastRainLatticeGpuMilliseconds;
    }

    /** T210. The rain target the resolve composites this frame (lattice-sized when the lattice pass ran). */
    public static RenderTarget lastRainCompositeTarget() {
        return lastRainCompositeTarget;
    }

    /** T205. Milliseconds the rain pass cost on the GPU, or 0 when the frame drew none. */
    public static float lastRainPassGpuMilliseconds() {
        return lastRainPassGpuMilliseconds;
    }

    /** T207. True when the last march wrote a temporal phase the hook must resolve. */
    public static boolean temporalPhaseActive() {
        return temporalPhaseActive;
    }

    public static Matrix4f lastInvProj() {
        return lastInvProj;
    }

    public static Matrix4f lastInvViewRot() {
        return lastInvViewRot;
    }

    public static Matrix4f lastViewProj() {
        return lastViewProj;
    }

    public static float lastMaxRenderDistance() {
        return lastMaxRenderDistance;
    }

    /** T207. The rendered-frame counter the phases are aged against. */
    public static long renderedFrameIndex() {
        return frameIndex;
    }

    /** Identifies a fresh completed GPU timestamp result without using a frame-time proxy. */
    static long lastGpuTimingSample() {
        return GPU_TIMER.getLastResultSerial();
    }

    public static float governorStepScale() {
        return GOVERNOR.stepScale();
    }

    /**
     * T098 phase 1 control arm, in raymarch iterations. Zero is production and
     * leaves the shader's MAX_STEPS cap untouched; a positive value raises the
     * cap so budget exhaustion can be removed as a variable while every other
     * march rule stays fixed. Set only by the marker-gated capture driver.
     */
    private static int diagnosticStepBudget;

    /** Test-only: see {@link #diagnosticStepBudget}. */
    public static void setDiagnosticStepBudget(int steps) {
        diagnosticStepBudget = Math.max(0, Math.min(384, steps));
    }

    /** Test-only: the step budget currently forced, or zero for production. */
    public static int diagnosticStepBudget() {
        return diagnosticStepBudget;
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
        // T207. Whatever invalidates the ping-pong history - a camera cut, a
        // resize, a reload, a level change, a teleport - invalidates the raw
        // phases for the same reason: their samples no longer correspond to
        // what this camera sees.
        CloudTemporalPhaseState.invalidateAll();
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
        RAIN_FIELD_TIMER.close();
        // T206. The gate and rain-pass clocks are production clocks now and
        // release their queries with the others.
        RAIN_GATE_TIMER.close();
        RAIN_PASS_TIMER.close();
        RAIN_LATTICE_TIMER.close();
        lastRainLatticeGpuMilliseconds = -1.0F;
        lastRainCompositeTarget = null;
        lastRainFieldGpuMilliseconds = -1.0F;
        lastRainGateGpuMilliseconds = -1.0F;
        lastRainPassGpuMilliseconds = -1.0F;
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
    /** True once a cloud draw has uploaded a transform this session. */
    public static boolean lastTransformValid() {
        return lastTransformValid;
    }

    /**
     * Projects a world position to the normalized device coordinates the cloud
     * shader would give it, using the last uploaded transform. Returns null
     * when no draw has happened yet or the point is behind the camera.
     */
    public static Vector3f projectWorldToNdc(double worldX, double worldY, double worldZ) {
        if (!lastTransformValid) {
            return null;
        }
        org.joml.Vector4f clip = new org.joml.Vector4f(
                (float) (worldX - lastCameraPosition.x),
                (float) (worldY - lastCameraPosition.y),
                (float) (worldZ - lastCameraPosition.z),
                1.0F
        );
        lastViewRotation.transform(clip);
        lastCloudProjection.transform(clip);
        if (clip.w <= 0.0001F) {
            return null;
        }
        return new Vector3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w);
    }

    public static Vector3f lastCameraPosition() {
        return new Vector3f(lastCameraPosition);
    }

    /**
     * Near and far planes of the transform the cloud pass was last drawn with.
     * The volume is marched out to MaxRenderDistance, which is independent of
     * this frustum, so a cloud can be integrated well beyond the far plane -
     * and any depth written for such a point saturates.
     */
    public static float[] lastProjectionNearFar() {
        if (!lastTransformValid) {
            return new float[] {Float.NaN, Float.NaN};
        }
        float m22 = lastCloudProjection.m22();
        float m32 = lastCloudProjection.m32();
        float near = m32 / (m22 - 1.0F);
        float far = m32 / (m22 + 1.0F);
        return new float[] {near, far};
    }

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
        StormOptimizationDiagnosticMode optimizationMode =
                VolumetricCloudDebugConfig.optimizationDiagnosticMode();
        VolumetricCloudRaymarchDebugView debugView = StormMaterialRuntimeTrace.active()
                ? VolumetricCloudRaymarchDebugView.STORM_MATERIAL_TRACE
                : StormWorkloadRuntimeCapture.active()
                    ? StormWorkloadRuntimeCapture.view()
                    : VolumetricCloudDebugConfig.raymarchDebugView();
        CoreCostDiagnosticProgram program = selectProgram(debugView, optimizationMode);
        ShaderInstance shader = VolumetricCloudShaders.volumeShader(program);
        if (shader == null && program == CoreCostDiagnosticProgram.LEAN_FINAL) {
            // Never quietly substitute the monolith here. It would render the
            // correct image at the pre-T161 cost, so the regression would be
            // invisible in every image check and only show up as lost frames.
            throw new LeanFinalProgramUnavailableException(
                    "lean FINAL cloud program (cloud_atmosphere_volume_final) failed to link;"
                            + " refusing to fall back to the diagnostic monolith");
        }
        if (shader == null || mainTarget == null || !weather.rendered()) {
            return false;
        }
        if (!CloudNoiseTextureManager.ensureReady()) {
            return false;
        }
        float cameraCloudDensity = CameraCloudDensityTracker.smoothedCameraDensity();
        // Historically ULTRA's 0.75 target exceeded one million fragments at
        // 1080p and dense whiteout could not resolve that extra sampling, so
        // this clamped it to 0.50 on an interior view. After the Rank 1 ladder
        // no mode exceeds 0.25, so min(profile, 0.50) is now always the profile
        // and this override is inert. It is retained rather than deleted
        // because it is the hook a later adaptive policy would reuse, and its
        // hysteresis still prevents repeated target rebuilds at the boundary.
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
        RAIN_FIELD_TIMER.poll();
        RAIN_GATE_TIMER.poll();
        RAIN_PASS_TIMER.poll();
        lastRainPassGpuMilliseconds = program.rainSplitCloud()
                ? Math.max(0.0F, RAIN_PASS_TIMER.getLastMilliseconds())
                : 0.0F;
        RAIN_LATTICE_TIMER.poll();
        lastRainLatticeGpuMilliseconds = program.rainSplitCloud() && lastRainLatticeRan
                ? Math.max(0.0F, RAIN_LATTICE_TIMER.getLastMilliseconds())
                : 0.0F;
        // The timer holds its last resolved result, so a frame that builds no
        // field would otherwise report the previous arm's build cost and charge
        // a control for a pass it never ran. Zero unless this frame generated.
        lastRainFieldGpuMilliseconds = program.rainFieldGeneration()
                || (program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH
                    && VolumetricCloudDebugConfig.rainFieldForcedOnMonolith())
                // T196. The storm field builds on the same clock, so the
                // campaign report's field column is its build cost; T199: on
                // FINAL this is the production build, every frame.
                || stormFieldProgram(program)
                ? RAIN_FIELD_TIMER.getLastMilliseconds()
                : 0.0F;
        // T203. The gate build on its own clock, and added to the field
        // column so a gate arm's net delta against a FINAL anchor charges it.
        lastRainGateGpuMilliseconds = rainGateProgram(program)
                ? Math.max(0.0F, RAIN_GATE_TIMER.getLastMilliseconds())
                : 0.0F;
        if (rainGateProgram(program) && lastRainFieldGpuMilliseconds >= 0.0F) {
            lastRainFieldGpuMilliseconds += lastRainGateGpuMilliseconds;
        }
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
        // T207. Temporal reconstruction: the march writes this frame's raw
        // phase into its own target (same dimensions, so every downstream
        // uniform is unchanged; only the destination differs) at that phase's
        // subpixel offset, and the hook resolves the four phases afterwards.
        // Only a FINAL-class frame is a phase: a debug view or a campaign
        // program that is not FINAL's image must not enter the phase set.
        boolean temporal = VolumetricCloudDebugConfig.temporalMode() != 0
                && profile.temporalEnabled()
                && program.normalProductionOutput()
                && debugView == VolumetricCloudRaymarchDebugView.FINAL
                && !StormProductionRayTrace.active()
                && !optimizationMode.t153Oracle();
        if (temporal) {
            // Advanced before the target is chosen, so the phase marched this
            // frame is the one the resolve treats as current.
            CloudTemporalPhaseState.advance();
            if (VolumetricCloudRenderTargets.preparePhaseTargets(
                    cloudTarget.width, cloudTarget.height)) {
                cloudTarget = VolumetricCloudRenderTargets.phaseTarget(
                        CloudTemporalPhaseState.currentPhase());
            } else {
                temporal = false;
            }
        } else if (VolumetricCloudDebugConfig.temporalMode() == 0) {
            VolumetricCloudRenderTargets.releaseTemporalTargets("temporalOff");
        }
        temporalPhaseActive = temporal;
        boolean t153Oracle = optimizationMode.t153Oracle();
        RenderTarget oracleTarget = t153Oracle
                ? VolumetricCloudRenderTargets.prepareVisibleVolumeOracleTarget(
                        cloudTarget.width, cloudTarget.height)
                : null;
        if (!t153Oracle) {
            VolumetricCloudRenderTargets.releaseVisibleVolumeOracleTarget();
        }
        // History is only consumed when the ping-pong target actually holds
        // last frame's clouds and no camera cut/resize/no-cloud frame
        // invalidated it since. Everything else ghosts.
        Tuning safeTuning = tuning == null ? Tuning.CELLS : tuning;
        // T207. A raw phase never blends the ping-pong history: the phases
        // are original march output, and the resolve is the only temporal
        // combination.
        boolean historyValid = !temporal
                && VolumetricCloudDebugConfig.historyEnabled()
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

        bindMarchTarget(cloudTarget, temporal);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        SceneDepthFrame safeSceneDepth = sceneDepth == null ? SceneDepthFrame.INVALID : sceneDepth;
        // T203. Recorded so the gate build program can be given the same
        // samplers as the ray program without a second list to maintain.
        frameSamplers.clear();
        frameSamplers.put("WeatherMapSampler", weatherTarget.getColorTextureId());
        frameSamplers.put("MorphologyMapSampler", morphologyTarget.getColorTextureId());
        frameSamplers.put("CumulusStageSupportMapSampler", cumulusStageSupportTarget.getColorTextureId());
        frameSamplers.put("CumulusStageBaseMapSampler", cumulusStageBaseTarget.getColorTextureId());
        frameSamplers.put("CumulusStageTopMapSampler", cumulusStageTopTarget.getColorTextureId());
        frameSamplers.put("StormCandidateMapSampler", stormCandidateTarget.getColorTextureId());
        frameSamplers.put("StormDescriptorSampler", stormDescriptorTarget.getColorTextureId());
        frameSamplers.put("BlueNoiseSampler", CloudNoiseTextureManager.blueNoiseTextureId());
        frameSamplers.put("SceneDepthSampler", safeSceneDepth.valid() ? safeSceneDepth.textureId() : 0);
        frameSamplers.put("HistorySampler", historyValid ? historyTarget.getColorTextureId() : 0);
        frameSamplers.put("HistoryDepthSampler", historyValid ? historyTarget.getDepthTextureId() : 0);
        frameSamplers.put("OracleIntervalSampler", 0);
        setFrameSamplers(shader);
        // T188. Not a JSON sampler; see bindManualTextures.
        rainFieldTextureId = 0;
        stormFieldTextureId = 0;
        rainPassTextureId = 0;

        lastCloudProjection.set(projection);
        lastViewRotation.set(viewRotation);
        lastCameraPosition.set(cameraPos);
        lastTransformValid = true;
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
        // T188 Task 0. The rain-heavy fixture, as a precipitation forcing on
        // the existing deterministic storm rather than a second world.
        //
        // Descriptor-owned columns take their intensity from MaxPrecipitation,
        // so raising it makes every column the storm owns rain-bearing while
        // leaving raster-precipitation columns exactly as they were. That is
        // precisely the population the rain field governs, and T186 could not
        // measure missed rain because the ordinary fixture leaves it almost
        // empty - 11,028 rain-positive segments in a frame is too sparse for
        // continuity or onset statistics to mean anything.
        //
        // Applied only to the rain-only capture pair, and identically to both
        // sides of it, so it changes what is visible and not what is compared.
        float paMaxPrecipitation = weather.maxPrecipitation();
        if (program.rainMaskCapture() || VolumetricCloudDebugConfig.rainHeavyForced()) {
            paMaxPrecipitation = Math.max(paMaxPrecipitation, 0.85F);
        }
        lastNaturalMaxPrecipitation = weather.maxPrecipitation();
        float paPrecipitationOverride = VolumetricCloudDebugConfig.precipitationOverride();
        if (Float.isFinite(paPrecipitationOverride)) {
            // T225. The stationary rain ramp. Diagnostic only; NaN in production.
            paMaxPrecipitation = paPrecipitationOverride;
        }
        shader.safeGetUniform("MaxPrecipitation").set(paMaxPrecipitation);
        shader.safeGetUniform("PaLowerDeckRelief").set(VolumetricCloudDebugConfig.lowerDeckReliefMode());
        shader.safeGetUniform("PaStormGeometry").set(VolumetricCloudDebugConfig.stormGeometryMode());
        shader.safeGetUniform("PaReliefShade").set(VolumetricCloudDebugConfig.reliefShadeStrength());
        // T224. Where the shader's cameraStartsInsideSlab turns on: the ray
        // volume's floor, SlabBaseY lowered by precipitationRayPadding(). Not
        // modelled: the shader lowers it further when funnels are present.
        float paPaddingEdge = Math.max(0.0F, Math.min(1.0F, (paMaxPrecipitation - 0.02F) / 0.83F));
        lastRayVolumeFloorY = weather.slabBaseY()
                - 180.0F * paPaddingEdge * paPaddingEdge * (3.0F - 2.0F * paPaddingEdge);
        lastSlabBaseY = weather.slabBaseY();
        lastSlabTopY = weather.slabTopY();
        lastMaxPrecipitation = paMaxPrecipitation;
        shader.safeGetUniform("PuffLobeCount").set(PuffLobeSpatialIndex.lobeCount());
        // T162 scaling arm: a positive cap tells the shader about fewer
        // descriptors than are resident. Diagnostic only; the default is no cap.
        int residentLobeCount = StormGeometryBuildCoordinator.lobeCount();
        int descriptorLimit = VolumetricCloudDebugConfig.descriptorCountLimit();
        shader.safeGetUniform("StormLobeCount").set(
                descriptorLimit < 0 ? residentLobeCount
                        : Math.min(residentLobeCount, descriptorLimit));
        shader.safeGetUniform("PaDiagnosticStepBudget").set(diagnosticStepBudget);
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
        // T207. The phase's screen-space ray offset in texture-coordinate
        // units, and the deferred rain split; both zero on a non-temporal
        // frame so the march is byte-identical to T206's. Uniform state
        // persists between frames, so both are uploaded every frame.
        int phase = CloudTemporalPhaseState.currentPhase();
        shader.safeGetUniform("PaSampleJitter").set(
                temporal ? CloudTemporalPhaseState.jitterX(phase) / Math.max(1.0F, cloudTarget.width) : 0.0F,
                temporal ? CloudTemporalPhaseState.jitterY(phase) / Math.max(1.0F, cloudTarget.height) : 0.0F);
        // T208 attribution: the body-only reference is a non-temporal frame
        // with the split deferred and never composited.
        shader.safeGetUniform("PaTemporalPhase").set(
                temporal || VolumetricCloudDebugConfig.deferredSplitForced() ? 1 : 0);
        lastInvProj.set(invProj);
        lastInvViewRot.set(invViewRot);
        lastViewProj.set(projection).mul(viewRotation);
        shader.safeGetUniform("RaymarchSteps").set(profile.raymarchSteps());
        shader.safeGetUniform("LightSteps").set(profile.lightSteps());
        shader.safeGetUniform("ScatterOctaves").set(profile.scatterOctaves());
        // T147 detail-LOD ceiling: diagnostic-only, zero outside a capture.
        shader.safeGetUniform("DetailQuality").set(
                optimizationMode == StormOptimizationDiagnosticMode.T147_DETAIL_OFF
                        ? 0
                        : profile.detailQuality());
        shader.safeGetUniform("StepScale").set(stepScale);
        shader.safeGetUniform("ExteriorFineStep").set(
                PuffLobeSpatialIndex.exteriorFineStepWorld(profile.raymarchSteps(), stepScale)
        );
        // T211. The exterior ray-origin phase width (0 fineStep, 1 coarseStep).
        shader.safeGetUniform("PaOriginJitterMode").set(
                VolumetricCloudDebugConfig.originJitterMode());
        shader.safeGetUniform("PaMacroEnvelope").set(
                VolumetricCloudDebugConfig.macroEnvelopeMode());
        shader.safeGetUniform("PaLowerBodyFill").set(
                VolumetricCloudDebugConfig.lowerBodyMode());
        shader.safeGetUniform("PaDeepScatter").set(
                VolumetricCloudDebugConfig.deepScatterMode());
        float uploadedMaxRenderDistance = Math.max(300.0F, maxRenderDistance);
        lastMaxRenderDistance = uploadedMaxRenderDistance;
        boolean uploadedUseSceneDepth = sceneRayLimitEnabled && safeSceneDepth.valid();
        boolean uploadedCoveragePretestEnabled = VolumetricCloudDebugConfig.coveragePretestEnabled();
        int uploadedCoveragePretestSamples = VolumetricCloudDebugConfig.coveragePretestSamples();
        float uploadedCoveragePretestThreshold = VolumetricCloudDebugConfig.coveragePretestThreshold();
        int uploadedCoveragePretestDilation = VolumetricCloudDebugConfig.coveragePretestDilation();
        // T147 distance-LOD ceiling: diagnostic-only, unchanged outside a capture.
        shader.safeGetUniform("MaxRenderDistance").set(
                optimizationMode == StormOptimizationDiagnosticMode.T147_HALF_DISTANCE
                        ? Math.max(300.0F, uploadedMaxRenderDistance * 0.5F)
                        : uploadedMaxRenderDistance);
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
        shader.safeGetUniform("DebugView").set(debugView.shaderId());
        shader.safeGetUniform("StormTopologyMode").set(
                VolumetricCloudDebugConfig.stormTopologyMode().shaderId()
        );
        // T133 / SC-020: zero outside a diagnostic capture, so ordinary frames
        // take the production optimized paths.
        // Uploaded as exactly zero on every frame. T141's evaluation-amplify arm
        // perturbs its second evaluation by this so the compiler cannot fold
        // the call away; a zero perturbation keeps the result bit-identical.
        shader.safeGetUniform("PaDiagnosticEvalEpsilon").set(0.0F);
        shader.safeGetUniform("PaDiagnosticOptimizationMode").set(
                VolumetricCloudDebugConfig.optimizationDiagnosticMode().shaderFlags());
        shader.safeGetUniform("PaOraclePass").set(0);
        shader.safeGetUniform("PaOracleBaseSize").set(
                (float) cloudTarget.width, (float) cloudTarget.height);
        shader.safeGetUniform("StormTraceOrigin").set(
                StormMaterialRuntimeTrace.x(), StormMaterialRuntimeTrace.z()
        );
        shader.safeGetUniform("StormTraceYStart").set(StormMaterialRuntimeTrace.yStart());
        shader.safeGetUniform("StormTraceYInterval").set(StormMaterialRuntimeTrace.interval());
        shader.safeGetUniform("StormTraceSamples").set(StormMaterialRuntimeTrace.samples());
        shader.safeGetUniform("StormTraceStage").set(StormMaterialRuntimeTrace.stage());
        // T098 production ray trace. Zero for every ordinary frame: the shader
        // reaches no recording write and marches the fragment's own ray.
        StormProductionRayTrace.resolveAgainst(cloudTarget);
        shader.safeGetUniform("PaLegacyHitDepth").set(
                VolumetricCloudDebugConfig.t098LegacyHitDepth() ? 1 : 0);
        shader.safeGetUniform("PaLegacyFinePromotion").set(
                VolumetricCloudDebugConfig.t098LegacyFinePromotion() ? 1 : 0);
        shader.safeGetUniform("PaDiagnosticLightingMode").set(
                VolumetricCloudDebugConfig.diagnosticLightingMode());
        shader.safeGetUniform("PaRayTraceMode").set(StormProductionRayTrace.shaderMode());
        shader.safeGetUniform("PaRayTraceNdc").set(
                StormProductionRayTrace.ndcX(), StormProductionRayTrace.ndcY()
        );
        shader.safeGetUniform("PaRayTraceFragCoord").set(
                StormProductionRayTrace.fragCoordX(), StormProductionRayTrace.fragCoordY()
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

        if (t153Oracle) {
            // T153's ground-truth pass deliberately sits outside the timestamp
            // query. It runs the real production density/march and publishes
            // occupied intervals; only the replay below is the measured
            // ceiling. This is diagnostic infrastructure, not an occupancy
            // implementation and never executes for NORMAL_PRODUCTION.
            VolumetricCloudRenderTargets.clearAndBind(oracleTarget);
            shader.safeGetUniform("DebugView").set(
                    VolumetricCloudRaymarchDebugView.T153_ORACLE_GROUND_TRUTH.shaderId());
            shader.safeGetUniform("PaOraclePass").set(1);
            shader.apply();
            PuffLobeSpatialIndex.uploadDescriptors(shader.getId());
            bindManualTextures(shader, puffCandidateTarget.getColorTextureId());
            try {
                FullscreenQuad.draw(shader);
            } finally {
                unbindManualTextures();
                shader.clear();
            }

            bindMarchTarget(cloudTarget, temporal);
            shader.setSampler("OracleIntervalSampler", oracleTarget.getColorTextureId());
            shader.safeGetUniform("DebugView").set(debugView.shaderId());
            shader.safeGetUniform("PaOraclePass").set(0);
        }

        // T188. The monolith is the only program that can read workload
        // counters, so the campaign forces the field onto it to measure the
        // ray-side fetch count and the post-field attribution. Never applied to
        // a timed arm: an anchor would then pay for a pass it does not run.
        boolean paFieldProgram = program.rainFieldGeneration()
                || (program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH
                    && VolumetricCloudDebugConfig.rainFieldForcedOnMonolith());
        // T203. The rain gate field: the same target, pass and unit as
        // T188's field, a different build (selected by PaRainGateField in
        // the shader) and a different reader - the segment test's gate. The
        // monolith builds and reads it when a campaign forces the gate on.
        // T206: FINAL's frame builds it every frame; its rain pass reads it.
        boolean paGateProgram = rainGateProgram(program);
        if (paGateProgram) {
            // The build is its own program (RAIN_GATE_BUILD): the pass baked
            // on, the march absent. The first T203 run carried the build in
            // the ray program and the ray paid 2.5-4 ms for code it never
            // executed. The build program takes the frame's uniforms and
            // samplers from the ray program - the same values, uploaded once
            // more - so the field it writes is what the ray program's own
            // build branch would have written.
            ShaderInstance buildShader = VolumetricCloudShaders.volumeShader(
                    CoreCostDiagnosticProgram.RAIN_GATE_BUILD);
            RenderTarget rainGateTarget = buildShader == null ? null
                    : VolumetricCloudRenderTargets.prepareRainFieldTarget(
                            profile.weatherMapSize());
            if (rainGateTarget == null && program.rainSplitCloud()) {
                // T206. A split cloud program's rain pass bakes the gate on
                // and reads it on every sample; without the build it would
                // gate against zeros. FINAL never draws without its rain, and
                // never draws the monolith instead: the same loud failure as
                // a failed FINAL link.
                throw new LeanFinalProgramUnavailableException(
                        "rain gate build program (cloud_atmosphere_volume_rain_gate_build)"
                                + (buildShader == null ? " failed to link" : "'s field target is unavailable")
                                + "; FINAL's rain pass reads the gate and must not draw without it");
            }
            if (rainGateTarget != null) {
                copyFrameUniforms(shader, buildShader);
                setFrameSamplers(buildShader);
                VolumetricCloudRenderTargets.clearAndBind(rainGateTarget);
                buildShader.safeGetUniform("PaStormFieldPass").set(0);
                buildShader.apply();
                PuffLobeSpatialIndex.uploadDescriptors(buildShader.getId());
                bindManualTextures(buildShader, puffCandidateTarget.getColorTextureId());
                RAIN_GATE_TIMER.begin();
                try {
                    FullscreenQuad.draw(buildShader);
                } finally {
                    RAIN_GATE_TIMER.end();
                    unbindManualTextures();
                    buildShader.clear();
                }
                bindMarchTarget(cloudTarget, temporal);
                rainFieldTextureId = rainGateTarget.getColorTextureId();
                // The ray programs bake the value; the monolith is told. 2
                // would keep the gate off in the segment test (the build-cost
                // arm bakes it), the monolith reads.
                shader.safeGetUniform("PaRainGateField").set(1);
            } else if (program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH) {
                shader.safeGetUniform("PaRainGateField").set(0);
            }
        } else if (program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH) {
            // Uniform state persists on the monolith between frames (T196):
            // a campaign that stops forcing the gate must see it off.
            shader.safeGetUniform("PaRainGateField").set(0);
        }
        if (paFieldProgram) {
            // T188. One untimed-by-GPU_TIMER draw over the 512x512 weather
            // domain, writing the descriptor-derived rain triple per column.
            // It runs the same directStormRainSupportAt the ray used to call
            // per sample, so nothing here can drift from what the march would
            // have computed - the two are one function in one program.
            //
            // Timed on its own clock instead, because the architecture is only
            // worth having if build + lookup beats the traversal it replaced.
            RenderTarget rainFieldTarget =
                    VolumetricCloudRenderTargets.prepareRainFieldTarget(
                            profile.weatherMapSize());
            if (rainFieldTarget != null) {
                VolumetricCloudRenderTargets.clearAndBind(rainFieldTarget);
                shader.safeGetUniform("PaRainFieldPass").set(1);
                shader.safeGetUniform("PaRainFieldEnabled").set(0);
                // T190. Classification is part of the build, so it is set for
                // the generation pass and for the lookup alike. The monolith
                // gets it too when the campaign forces the field onto it, or
                // every mixed-cell counter would read zero for want of a
                // classified field rather than for want of mixed cells.
                int paConservative = program.rainFieldConservative()
                        || (program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH
                            && VolumetricCloudDebugConfig.rainFieldForcedOnMonolith())
                        ? 1 : 0;
                shader.safeGetUniform("PaRainFieldConservative").set(paConservative);
                // T192. The closed-form triage, on the same rule: the
                // monolith gets it too when the campaign forces the field
                // onto it, or the census counters would describe a
                // classifier the run never used.
                int paClosedForm = program.rainFieldClosedForm()
                        || (program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH
                            && VolumetricCloudDebugConfig.rainFieldForcedOnMonolith())
                        ? 1 : 0;
                shader.safeGetUniform("PaRainFieldClosedForm").set(paClosedForm);
                shader.apply();
                PuffLobeSpatialIndex.uploadDescriptors(shader.getId());
                bindManualTextures(shader, puffCandidateTarget.getColorTextureId());
                RAIN_FIELD_TIMER.begin();
                try {
                    FullscreenQuad.draw(shader);
                } finally {
                    RAIN_FIELD_TIMER.end();
                    unbindManualTextures();
                    shader.clear();
                }

                bindMarchTarget(cloudTarget, temporal);
                rainFieldTextureId = rainFieldTarget.getColorTextureId();
                shader.safeGetUniform("PaRainFieldPass").set(0);
                shader.safeGetUniform("PaRainFieldConservative").set(paConservative);
                shader.safeGetUniform("PaRainFieldClosedForm").set(paClosedForm);
                shader.safeGetUniform("PaRainFieldEnabled").set(
                        program.rainFieldLookup()
                                || program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH
                                ? 1 : 0);
            }
        }

        // T196. Uniform state persists on a program between frames, so the
        // monolith must be told every frame whether it reads the field: the
        // first campaign run's FAR production capture kept the previous
        // forced frame's lookups enabled against no field at all.
        shader.safeGetUniform("PaStormFieldPass").set(0);
        shader.safeGetUniform("PaStormFieldEnabled").set(0);
        // T197 varied the grid; T199 pinned it. Every lean program bakes the
        // constant, and the monolith is uploaded the same value here so its
        // node mapping and the atlas below can never disagree.
        int stormFieldXZ = StormFieldGeometry.DEFAULT_XZ;
        shader.safeGetUniform("PaStormFieldXZ").set(stormFieldXZ);
        // T198. The audit's selectors ride with the field uniforms; only the
        // audit program declares them.
        shader.safeGetUniform("PaLightAuditVariant").set(
                VolumetricCloudDebugConfig.lightAuditVariant());
        shader.safeGetUniform("PaLightAuditOutput").set(
                VolumetricCloudDebugConfig.lightAuditOutput());
        // T196. The shared storm field. One draw over the atlas - XZ-square
        // by 32 nodes, each the storm body's threshold and height, the probe
        // floor and a detail-free density - before the timed draw, on the
        // field clock. T199: FINAL runs it every frame (its consumer bits are
        // baked on), the monolith runs it at production's bits unless a
        // campaign overrides them, so the counters describe the post-field
        // workload and a debug view renders FINAL's image.
        if (stormFieldProgram(program)) {
            RenderTarget stormFieldTarget =
                    VolumetricCloudRenderTargets.prepareStormFieldTarget(stormFieldXZ);
            if (stormFieldTarget == null
                    && program == CoreCostDiagnosticProgram.LEAN_FINAL) {
                // The lean program reads the atlas unconditionally, so a frame
                // without one would light the cone and probe the scan against
                // zeros: wrong, and silently so. Same loud fallback as a
                // failed link.
                throw new LeanFinalProgramUnavailableException(
                        "storm field atlas (" + StormFieldGeometry.describe(stormFieldXZ)
                                + ") unavailable; FINAL bakes the field consumers on"
                                + " and must not draw without it");
            }
            if (stormFieldTarget != null) {
                int enabledBits = program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH
                        ? VolumetricCloudDebugConfig.stormFieldForcedOnMonolith()
                        : program.lightAuditCapture()
                        // T198. The audit measures whichever lookup the
                        // campaign selects; its consumer bits are live.
                        ? VolumetricCloudDebugConfig.lightAuditFieldBits()
                        : program.stormFieldEnabledBits();
                if (VolumetricCloudDebugConfig.consumeStormFieldSoundnessRequest()) {
                    runStormFieldSoundnessOracle(shader, puffCandidateTarget, stormFieldXZ);
                }
                VolumetricCloudRenderTargets.clearAndBind(stormFieldTarget);
                shader.safeGetUniform("PaStormFieldPass").set(1);
                // T202. The build reads the consumer bits too: bit 32 decides
                // what the node's A channel carries (the refinement's
                // clearance floor, or the node density). FINAL bakes the bits,
                // so its build has always seen them; the monolith is uploaded
                // the same value here so its atlas matches FINAL's. The build
                // pass returns before any consumer lookup, so nothing in it
                // reads the atlas it is writing.
                shader.safeGetUniform("PaStormFieldEnabled").set(enabledBits);
                shader.apply();
                PuffLobeSpatialIndex.uploadDescriptors(shader.getId());
                bindManualTextures(shader, puffCandidateTarget.getColorTextureId());
                countStormFieldBuild(weather, materialOffsetX, materialOffsetZ);
                RAIN_FIELD_TIMER.begin();
                try {
                    FullscreenQuad.draw(shader);
                } finally {
                    RAIN_FIELD_TIMER.end();
                    unbindManualTextures();
                    shader.clear();
                }
                bindMarchTarget(cloudTarget, temporal);
                stormFieldTextureId = stormFieldTarget.getColorTextureId();
                shader.safeGetUniform("PaStormFieldPass").set(0);
                shader.safeGetUniform("PaStormFieldEnabled").set(enabledBits);
            }
        }

        // T205. The rain pass, drawn before the split cloud program with the
        // frame's uniforms and samplers (the gate field and the storm field
        // are both built by now): a rain-only march into a cloud-sized
        // target the cloud program then composites at the rain's depth.
        // T206: the production render order is field build, gate build,
        // rain pass, split cloud, then the composite/output path.
        lastRainCompositeTarget = null;
        if (program.rainSplitCloud()) {
            ShaderInstance rainShader = VolumetricCloudShaders.volumeShader(
                    program.rainPassProgram());
            // T210. On a temporal frame the rain can be evaluated at the
            // lattice (twice the phase grid): mode 1 one pass at that size,
            // mode 2 the phase-grid pass first and the lattice pass masked
            // by it. Off, the target is the phase grid as before.
            int rainLatticeMode = temporal ? VolumetricCloudDebugConfig.rainLatticeMode() : 0;
            int rainPassScale = rainLatticeMode == 1 ? 2 : 1;
            RenderTarget rainPassTarget = rainShader == null ? null
                    : VolumetricCloudRenderTargets.prepareRainPassTarget(
                            cloudTarget.width * rainPassScale, cloudTarget.height * rainPassScale);
            RenderTarget rainLatticeTarget = rainShader == null || rainLatticeMode != 2 ? null
                    : VolumetricCloudRenderTargets.prepareRainLatticeTarget(
                            cloudTarget.width * 2, cloudTarget.height * 2);
            if (rainLatticeMode != 2) {
                VolumetricCloudRenderTargets.releaseRainLatticeTarget("modeOff");
            }
            lastRainLatticeRan = false;
            shader.safeGetUniform("PaRainCoarseMask").set(0);
            lastRainCompositeTarget = rainPassTarget;
            if (rainPassTarget == null && program.rainPassProgram().isProductionProgram()) {
                // T206. The split cloud program fetches the rain-pass texel
                // unconditionally; against no pass it would composite a zero
                // transmittance into every pixel. Fail FINAL loudly instead.
                throw new LeanFinalProgramUnavailableException(
                        "rain pass program (cloud_atmosphere_volume_rain_pass)"
                                + (rainShader == null ? " failed to link" : "'s target is unavailable")
                                + "; FINAL composites the rain pass and must not draw without it");
            }
            if (rainPassTarget != null && VolumetricCloudDebugConfig.rainDisabled()) {
                // T208 attribution. No rain pass: the target is cleared to
                // "no rain" (rgb 0, packed T_r = 1) so the split cloud
                // program and the resolve composite nothing. Diagnostic only.
                rainPassTarget.setClearColor(0.0F, 0.0F, 0.0F, 4095.0F);
                VolumetricCloudRenderTargets.clearAndBind(rainPassTarget);
                rainPassTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                bindMarchTarget(cloudTarget, temporal);
                rainPassTextureId = rainPassTarget.getColorTextureId();
            } else if (rainPassTarget != null) {
                copyFrameUniforms(shader, rainShader);
                setFrameSamplers(rainShader);
                VolumetricCloudRenderTargets.clearAndBind(rainPassTarget);
                rainShader.safeGetUniform("PaStormFieldPass").set(0);
                // T207. Rain is current-frame and unjittered: the rain pass
                // marches the block-centre ray whatever phase the cloud march
                // is on, so the texel a block composites is the same on
                // every frame of the cycle (a jittered rain pass rotated
                // through four subpixel rays - a four-frame rain flicker).
                rainShader.safeGetUniform("PaSampleJitter").set(0.0F, 0.0F);
                rainShader.safeGetUniform("PaRainPassScale").set(1);
                rainShader.safeGetUniform("PaRainCoarseMask").set(0);
                rainShader.apply();
                PuffLobeSpatialIndex.uploadDescriptors(rainShader.getId());
                bindManualTextures(rainShader, puffCandidateTarget.getColorTextureId());
                RAIN_PASS_TIMER.begin();
                try {
                    FullscreenQuad.draw(rainShader);
                } finally {
                    RAIN_PASS_TIMER.end();
                    unbindManualTextures();
                    rainShader.clear();
                }
                rainPassTextureId = rainPassTarget.getColorTextureId();
                if (rainLatticeTarget != null) {
                    // T210 mode 2. The lattice pass, masked by the coarse
                    // result it reads through RainPassSampler; the phase
                    // program and the resolve then read the lattice target.
                    VolumetricCloudRenderTargets.clearAndBind(rainLatticeTarget);
                    rainShader.safeGetUniform("PaRainCoarseMask").set(1);
                    rainShader.apply();
                    PuffLobeSpatialIndex.uploadDescriptors(rainShader.getId());
                    bindManualTextures(rainShader, puffCandidateTarget.getColorTextureId());
                    RAIN_LATTICE_TIMER.begin();
                    try {
                        FullscreenQuad.draw(rainShader);
                    } finally {
                        RAIN_LATTICE_TIMER.end();
                        unbindManualTextures();
                        rainShader.clear();
                    }
                    rainPassTextureId = rainLatticeTarget.getColorTextureId();
                    lastRainCompositeTarget = rainLatticeTarget;
                    lastRainLatticeRan = true;
                }
                bindMarchTarget(cloudTarget, temporal);
            } else {
                ProjectAtmosphere.LOGGER.warn(
                        "[VolumetricClouds] rain pass program unavailable for {}",
                        program.serializedName());
            }
        }

        // T210. The split reads the rain texel of its own lattice site when
        // the target it composites is lattice-sized (mode 1, or mode 2 with
        // the lattice pass run); the phase grid otherwise.
        shader.safeGetUniform("PaRainPassScale").set(
                lastRainCompositeTarget != null
                        && lastRainCompositeTarget.width == cloudTarget.width * 2 ? 2 : 1);
        lastProgram = program;
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

        // A ray-trace pass writes a diagnostic record, not an image. It must
        // never become the next frame's history.
        renderedProductionFrame = program.normalProductionOutput()
                && debugView == VolumetricCloudRaymarchDebugView.FINAL
                && !StormProductionRayTrace.active()
                && !t153Oracle;
        if (renderedProductionFrame) {
            prevProj.set(projection);
            prevViewRot.set(viewRotation);
            prevCameraPos.set(cameraPos);
            prevMaterialOffset.set(materialOffsetX, materialOffsetZ);
            previousHistoryKey = currentHistoryKey;
            hasPrevFrame = true;
        }
        if (temporal && renderedProductionFrame) {
            // T207. The raw phase just marched, with the camera it was seen
            // from and the frame it was rendered on. Only a raw march is ever
            // recorded; the resolve's output never comes through here.
            CloudTemporalPhaseState.record(
                    CloudTemporalPhaseState.currentPhase(), lastViewProj, cameraPos, frameIndex);
        }
        frameIndex++;
        return true;
    }

    /**
     * T207. Binds the march's destination: the ping-pong cloud target, or
     * the raw phase target with its aux attachment drawn and cleared.
     */
    private static void bindMarchTarget(RenderTarget target, boolean temporal) {
        if (temporal) {
            VolumetricCloudRenderTargets.clearAndBindPhase(target);
        } else {
            VolumetricCloudRenderTargets.clearAndBind(target);
        }
    }

    /**
     * Chooses the linked program for this frame.
     *
     * <p>The lean FINAL build has every diagnostic selector baked in as a
     * constant, so it is only correct for a frame that would have uploaded
     * exactly those values. This predicate is the runtime half of that
     * contract: each clause corresponds to one entry of {@code
     * leanFinalConstants} in {@code build.gradle}, and the two must be edited
     * together. Anything diagnostic - a debug view, a trace, an oracle replay,
     * an optimization arm, a legacy evidence arm, a stage or tier cut, a
     * ray-trace record, a step budget - falls through to the unmodified
     * monolith, which still contains all of it.
     */
    private static CoreCostDiagnosticProgram selectProgram(
            VolumetricCloudRaymarchDebugView debugView,
            StormOptimizationDiagnosticMode optimizationMode
    ) {
        CoreCostDiagnosticProgram override = VolumetricCloudDebugConfig.finalProgramOverride();
        if (override != null) {
            return override;
        }
        return leanFinalEligible(debugView, optimizationMode)
                ? CoreCostDiagnosticProgram.LEAN_FINAL
                : CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH;
    }

    /** True when every uniform the lean program literalizes is at its baked value. */
    private static boolean leanFinalEligible(
            VolumetricCloudRaymarchDebugView debugView,
            StormOptimizationDiagnosticMode optimizationMode
    ) {
        return debugView == VolumetricCloudRaymarchDebugView.FINAL
                && diagnosticStepBudget == 0
                && VolumetricCloudDebugConfig.puffDensityStage()
                        == VolumetricPuffDensityStage.FINAL
                && VolumetricCloudDebugConfig.puffTierFilter() == VolumetricPuffTierFilter.ALL
                && optimizationMode == StormOptimizationDiagnosticMode.NORMAL_PRODUCTION
                && !StormProductionRayTrace.active()
                && !StormMaterialRuntimeTrace.active()
                && !VolumetricCloudDebugConfig.t098LegacyHitDepth()
                && !VolumetricCloudDebugConfig.t098LegacyFinePromotion()
                && VolumetricCloudDebugConfig.diagnosticLightingMode() == 0
                // T239. Every lean program bakes the lobe union; the continuous
                // body is an experimental arm and takes the monolith.
                && VolumetricCloudDebugConfig.stormGeometryMode() == 0
                // T232. FINAL bakes T231's relief and shade; any other value
                // is an arm and takes the monolith, where they are live.
                && VolumetricCloudDebugConfig.lowerDeckReliefMode()
                        == VolumetricCloudDebugConfig.PRODUCTION_LOWER_DECK_RELIEF_MODE
                && VolumetricCloudDebugConfig.reliefShadeStrength()
                        == VolumetricCloudDebugConfig.PRODUCTION_RELIEF_SHADE
                // T199. The field rows of leanFinalConstants: consumer bits
                // 11 and the audit selectors 0 (the grid, 256, has no runtime
                // state since T199h). A campaign that moves the monolith off
                // production's lookup or takes an audit capture is uploading
                // a value FINAL has baked differently and takes the monolith.
                && VolumetricCloudDebugConfig.stormFieldForcedOnMonolith()
                        == StormFieldGeometry.PRODUCTION_CONSUMER_BITS
                && VolumetricCloudDebugConfig.lightAuditVariant() == 0
                && VolumetricCloudDebugConfig.lightAuditOutput() == 0
                // T203. FINAL bakes the rain gate off; a campaign forcing it
                // on the monolith is uploading a value FINAL cannot take.
                && !VolumetricCloudDebugConfig.rainGateForcedOnMonolith();
    }

    /**
     * Raised when a FINAL frame cannot bind the lean program.
     *
     * <p>It propagates to the render hook's handler, which disables the
     * volumetric pass for the session and logs the cause - the established
     * native fallback. That is deliberately louder than substituting the
     * monolith: the monolith would draw the same image while silently costing
     * what T161 removed, so a link failure has to be visible rather than merely
     * slow. Its simple name reaches the user through the render status string.
     */
    public static final class LeanFinalProgramUnavailableException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        LeanFinalProgramUnavailableException(String message) {
            super(message);
        }
    }

    /** Which program the last frame actually bound; for diagnostics and status. */
    public static CoreCostDiagnosticProgram lastProgram() {
        return lastProgram;
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
        // T188. Bound here rather than through the shader JSON: Minecraft
        // assigns JSON samplers to consecutive units from 0 and tracks only
        // twelve, so a thirteenth throws inside ShaderInstance.apply. A
        // PA-owned unit is the same mechanism the puff candidate map and both
        // noise volumes already use.
        bind2dSampler(
                program,
                "RainFieldSampler",
                RAIN_FIELD_TEXTURE_UNIT,
                rainFieldTextureId
        );
        // T196. T203: on its own unit, since the gate programs keep both
        // fields; bind2dSampler skips a zero texture id and a missing uniform.
        bind2dSampler(
                program,
                "StormFieldSampler",
                STORM_FIELD_TEXTURE_UNIT,
                stormFieldTextureId
        );
        // T205. The rain pass's result, for the split cloud program only.
        bind2dSampler(
                program,
                "RainPassSampler",
                RAIN_PASS_TEXTURE_UNIT,
                rainPassTextureId
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

    /**
     * T196. Programs that build the field this frame: every program whose
     * consumer bits are baked (T199: FINAL and its lean variants), plus the
     * monolith whenever its uploaded bits are non-zero - production's by
     * default, a campaign's override otherwise.
     */
    /** T199e. Count-only, see the field declarations; never skips a build. */
    private static void countStormFieldBuild(
            CloudWeatherMapRenderer.Result weather, float materialOffsetX, float materialOffsetZ) {
        long descriptors = PuffLobeSpatialIndex.descriptorSignatureForDiagnostics() * 31L
                + PuffLobeSpatialIndex.candidateSignatureForDiagnostics();
        long signature = descriptors;
        signature = signature * 31L + CloudWeatherMapRenderer.cacheMisses();
        signature = signature * 31L + Double.doubleToLongBits(weather.originX());
        signature = signature * 31L + Double.doubleToLongBits(weather.originZ());
        signature = signature * 31L + Float.floatToIntBits(weather.slabBaseY());
        signature = signature * 31L + Float.floatToIntBits(materialOffsetX);
        signature = signature * 31L + Float.floatToIntBits(materialOffsetZ);
        stormFieldBuilds++;
        if (stormFieldInputSignatureValid) {
            if (signature == stormFieldInputSignature) {
                stormFieldBuildsInputsUnchanged++;
            }
            if (descriptors == lastStormFieldDescriptorSignature) {
                stormFieldBuildsDescriptorsUnchanged++;
            }
        }
        stormFieldInputSignature = signature;
        lastStormFieldDescriptorSignature = descriptors;
        stormFieldInputSignatureValid = true;
    }

    /** T199e. {@code builds=.. unchangedInputs=.. unchangedDescriptors=..} since the last reset. */
    public static String stormFieldRebuildStatus() {
        return "builds=" + stormFieldBuilds
                + " unchangedInputs=" + stormFieldBuildsInputsUnchanged
                + " unchangedDescriptors=" + stormFieldBuildsDescriptorsUnchanged;
    }

    public static void resetStormFieldRebuildCounters() {
        stormFieldBuilds = 0L;
        stormFieldBuildsInputsUnchanged = 0L;
        stormFieldBuildsDescriptorsUnchanged = 0L;
        stormFieldInputSignatureValid = false;
    }

    private static boolean stormFieldProgram(CoreCostDiagnosticProgram program) {
        return program.stormFieldGeneration()
                || (program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH
                    && VolumetricCloudDebugConfig.stormFieldForcedOnMonolith() != 0);
    }

    /** T203. The frame's JSON samplers by name, for the ray program and the gate build program. */
    private static final java.util.Map<String, Integer> frameSamplers =
            new java.util.LinkedHashMap<>();

    private static void setFrameSamplers(ShaderInstance target) {
        for (java.util.Map.Entry<String, Integer> sampler : frameSamplers.entrySet()) {
            target.setSampler(sampler.getKey(), sampler.getValue());
        }
    }

    /**
     * T203. The names of every uniform the monolith declares, read once from
     * its JSON: the set the frame uploads to whichever program draws, and so
     * the set the gate build program can receive from the ray program.
     */
    private static java.util.List<String> monolithUniformNames;

    private static java.util.List<String> monolithUniformNames() {
        if (monolithUniformNames != null) {
            return monolithUniformNames;
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        try (java.io.Reader reader = new java.io.InputStreamReader(
                net.minecraft.client.Minecraft.getInstance().getResourceManager()
                        .getResourceOrThrow(net.minecraft.resources.ResourceLocation
                                .fromNamespaceAndPath(ProjectAtmosphere.MODID,
                                        "shaders/core/cloud_atmosphere_volume.json"))
                        .open(),
                java.nio.charset.StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject json =
                    com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for (com.google.gson.JsonElement uniform : json.getAsJsonArray("uniforms")) {
                names.add(uniform.getAsJsonObject().get("name").getAsString());
            }
        } catch (java.io.IOException | RuntimeException failure) {
            ProjectAtmosphere.LOGGER.error(
                    "[VolumetricClouds] could not read the volume shader's uniform list;"
                            + " the rain gate build program would draw with stale uniforms",
                    failure);
        }
        monolithUniformNames = names;
        return names;
    }

    /**
     * T203. Copies every uniform value the frame set on {@code from} to the
     * same-named uniform of {@code to}, by the uniform's own type: ints by
     * component, floats and matrices as the flat buffer. A uniform either
     * program baked away is skipped - the constant it bakes is the value.
     */
    private static void copyFrameUniforms(ShaderInstance from, ShaderInstance to) {
        for (String name : monolithUniformNames()) {
            com.mojang.blaze3d.shaders.Uniform source = from.getUniform(name);
            com.mojang.blaze3d.shaders.Uniform target = to.getUniform(name);
            if (source == null || target == null || source.getType() != target.getType()) {
                continue;
            }
            int type = source.getType();
            if (type <= com.mojang.blaze3d.shaders.Uniform.UT_INT4) {
                java.nio.IntBuffer ints = source.getIntBuffer();
                int count = source.getCount();
                target.setSafe(
                        ints.get(0),
                        count > 1 ? ints.get(1) : 0,
                        count > 2 ? ints.get(2) : 0,
                        count > 3 ? ints.get(3) : 0);
            } else {
                java.nio.FloatBuffer floats = source.getFloatBuffer();
                float[] values = new float[source.getCount()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = floats.get(i);
                }
                target.set(values);
            }
        }
    }

    /** T203. Programs that build the rain gate field this frame. */
    private static boolean rainGateProgram(CoreCostDiagnosticProgram program) {
        return program.rainGateGeneration()
                || (program == CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH
                    && VolumetricCloudDebugConfig.rainGateForcedOnMonolith());
    }

    /**
     * T196 Task 2. The probe floor's soundness, measured on every node. The
     * shader writes (floor, dense reference minimum, fraction of reference
     * samples that could carry material) per node into a float target; this
     * reads it back once and counts. A node whose floor exceeds its reference
     * is an underestimate - a probe there could declare empty where production
     * finds material - and the count must be zero.
     */
    private static void runStormFieldSoundnessOracle(
            ShaderInstance shader, RenderTarget puffCandidateTarget, int stormFieldXZ) {
        RenderTarget oracle =
                VolumetricCloudRenderTargets.prepareStormFieldOracleTarget(stormFieldXZ);
        if (oracle == null) {
            return;
        }
        try {
            VolumetricCloudRenderTargets.clearAndBind(oracle);
            shader.safeGetUniform("PaStormFieldPass").set(2);
            shader.safeGetUniform("PaStormFieldEnabled").set(0);
            shader.apply();
            PuffLobeSpatialIndex.uploadDescriptors(shader.getId());
            bindManualTextures(shader, puffCandidateTarget.getColorTextureId());
            try {
                FullscreenQuad.draw(shader);
            } finally {
                unbindManualTextures();
                shader.clear();
            }
            int width = oracle.width;
            int height = oracle.height;
            java.nio.FloatBuffer pixels =
                    org.lwjgl.BufferUtils.createFloatBuffer(width * height * 4);
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, oracle.getColorTextureId());
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            }
            long nodes = 0L;
            long uncovered = 0L;
            long fieldEmpty = 0L;
            long referenceEmpty = 0L;
            long boundary = 0L;
            long underestimates = 0L;
            long boundaryUnderestimates = 0L;
            double worstUnderestimate = 0.0D;
            long usefulEmpty = 0L;
            long[] overestimateHistogram = new long[8];
            double overestimateSum = 0.0D;
            long overestimateSamples = 0L;
            // The union distance's measured drop per block, by axis, over
            // every node that walked: the constants the dilation assumes
            // have to sit above the whole distribution.
            double maxLipschitzY = 0.0D;
            long[] lipschitzYHistogram = new long[10];
            long lipschitzSamples = 0L;
            long binaryMisses = 0L;
            long[] binaryMissSlack = new long[8];
            double worstBinarySlack = 0.0D;
            for (int index = 0; index < width * height; index++) {
                float floor = pixels.get(index * 4);
                float reference = pixels.get(index * 4 + 1);
                float slack = pixels.get(index * 4 + 2);
                float packed = pixels.get(index * 4 + 3);
                float materialCount = (float) Math.floor(packed / 1000.0F);
                float lipschitzY = packed - materialCount * 1000.0F;
                float materialFraction = materialCount / 43.0F;
                nodes++;
                if (floor > -5.0F && floor < 1.0F - 1.0e-6F) {
                    // Only nodes whose walk entered a group carry a drop.
                    lipschitzSamples++;
                    maxLipschitzY = Math.max(maxLipschitzY, lipschitzY);
                    lipschitzYHistogram[(int) Math.max(0.0D, Math.min(9.0D,
                            Math.floor(lipschitzY / 0.5D)))]++;
                }
                if (floor <= -5.0F) {
                    uncovered++;
                    continue;
                }
                boolean mixed = materialFraction > 0.0F && materialFraction < 1.0F;
                if (mixed) {
                    boundary++;
                }
                if (floor >= 1.0F) {
                    fieldEmpty++;
                }
                if (reference >= 1.0F) {
                    referenceEmpty++;
                    if (floor >= 1.0F) {
                        usefulEmpty++;
                    }
                }
                double gap = floor - reference;
                if (gap > 1.0e-4D) {
                    underestimates++;
                    if (mixed) {
                        boundaryUnderestimates++;
                    }
                    worstUnderestimate = Math.max(worstUnderestimate, gap);
                    if (floor >= 1.0F) {
                        // A binary miss: the node believed the whole cell was
                        // outside the envelope. How far outside it believed
                        // itself is the margin that would have covered it.
                        binaryMisses++;
                        int bucket = slack > 1.0e8F ? 7
                                : (int) Math.max(0.0D, Math.min(6.0D,
                                        Math.floor(Math.log(Math.max(slack, 1.0F)) / Math.log(2.0D))));
                        binaryMissSlack[bucket]++;
                        worstBinarySlack = Math.max(worstBinarySlack,
                                slack > 1.0e8F ? -1.0D : slack);
                    }
                } else if (reference < 1.0F) {
                    // How far below the true threshold the floor sits where
                    // material is possible: the price of the bound.
                    double over = Math.max(0.0D, -gap);
                    overestimateSum += over;
                    overestimateSamples++;
                    int bucket = (int) Math.max(0.0D, Math.min(7.0D, Math.floor(over / 0.1D)));
                    overestimateHistogram[bucket]++;
                }
            }
            StringBuilder histogram = new StringBuilder();
            for (int bucket = 0; bucket < overestimateHistogram.length; bucket++) {
                histogram.append(bucket == 0 ? "" : ",").append(overestimateHistogram[bucket]);
            }
            StringBuilder lipschitzY = new StringBuilder();
            for (int bucket = 0; bucket < lipschitzYHistogram.length; bucket++) {
                lipschitzY.append(bucket == 0 ? "" : ",").append(lipschitzYHistogram[bucket]);
            }
            StringBuilder missSlack = new StringBuilder();
            for (int bucket = 0; bucket < binaryMissSlack.length; bucket++) {
                missSlack.append(bucket == 0 ? "" : ",").append(binaryMissSlack[bucket]);
            }
            // T197. The resolution and its geometry lead the line, so the
            // oracles of one launch can be told apart.
            ProjectAtmosphere.LOGGER.info(
                    "T196_SOUNDNESS xz={} voxelXZ={} halfCellXZ={} atlas={}x{}"
                            + " nodes={} uncovered={} covered={} boundaryNodes={}"
                            + " referenceSamplesPerNode=43 underestimates={}"
                            + " boundaryUnderestimates={} worstUnderestimate={}"
                            + " fieldEmpty={} referenceEmpty={} usefulEmptyRetained={}"
                            + " usefulEmptyFraction={} overestimateSamples={}"
                            + " overestimateMean={} overestimateHistogram0.1={}"
                            + " lipschitzNodes={} maxLipschitzY={} lipschitzYHistogram0.5={}"
                            + " binaryMisses={} binaryMissSlackLog2={} worstBinarySlack={}",
                    stormFieldXZ,
                    String.format(java.util.Locale.ROOT, "%.4f",
                            StormFieldGeometry.voxelXZ(stormFieldXZ)),
                    String.format(java.util.Locale.ROOT, "%.4f",
                            StormFieldGeometry.halfCellXZ(stormFieldXZ)),
                    width, height,
                    nodes, uncovered, nodes - uncovered, boundary, underestimates,
                    boundaryUnderestimates,
                    String.format(java.util.Locale.ROOT, "%.5f", worstUnderestimate),
                    fieldEmpty, referenceEmpty, usefulEmpty,
                    String.format(java.util.Locale.ROOT, "%.4f",
                            referenceEmpty == 0L ? 0.0D
                                    : (double) usefulEmpty / (double) referenceEmpty),
                    overestimateSamples,
                    String.format(java.util.Locale.ROOT, "%.4f",
                            overestimateSamples == 0L ? 0.0D
                                    : overestimateSum / (double) overestimateSamples),
                    histogram, lipschitzSamples,
                    String.format(java.util.Locale.ROOT, "%.3f", maxLipschitzY),
                    lipschitzY, binaryMisses, missSlack,
                    String.format(java.util.Locale.ROOT, "%.2f", worstBinarySlack));
        } catch (RuntimeException failure) {
            // A diagnostic must never take the renderer down with it: the
            // campaign that asked for it still has a sweep to run.
            ProjectAtmosphere.LOGGER.error("T196_SOUNDNESS failed; the field build continues", failure);
        } finally {
            VolumetricCloudRenderTargets.releaseStormFieldOracleTarget();
        }
    }

    private static void unbindManualTextures() {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + PUFF_CANDIDATE_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + RAIN_FIELD_TEXTURE_UNIT);
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

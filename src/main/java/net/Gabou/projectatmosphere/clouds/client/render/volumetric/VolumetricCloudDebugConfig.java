package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * Client-only comparison switches for the weather-map volumetric renderer.
 * Defaults preserve the normal renderer. Commands may flip these at runtime
 * for A/B tests; no simulation, tuning, or generation state reads them.
 */
public final class VolumetricCloudDebugConfig {
    /**
     * T098 evidence arm. When true the cloud pass restores the pre-fix
     * behaviour in which a hit's depth could saturate to the composite's miss
     * sentinel. It exists so a before/after image pair can be captured on one
     * fixture, at one pose, in one run, differing only in the corrected line.
     * Never enabled by production; the T098 capture set turns it on for its
     * two comparison shots and clears it immediately after.
     */
    private static volatile boolean t098LegacyHitDepth;
    /**
     * T098 evidence arm. When true the marcher restores the pre-fix promotion,
     * which spent one march iteration per fine sample while crossing empty
     * coverage envelope. It exists so the cost and the image of the two
     * policies can be compared on one fixture in one run.
     */
    private static volatile boolean t098LegacyFinePromotion;
    /**
     * T136 cost-attribution arm. When true the cloud pass replaces lighting
     * with a constant radiance, so the GPU-time difference against production
     * is the lighting and self-shadow share. Never enabled by production.
     */
    private static volatile boolean t136ConstantLighting;
    /**
     * T221 lighting attribution views. 0 production; 1 constant radiance
     * (shape only); 2 light-path transmittance only; 3 local density.
     * Diagnostic; routes to the monolith. T136's boolean still forces 1.
     */
    private static volatile int diagnosticLightingMode;
    /**
     * T161 A/B arm. Null lets the renderer pick the program automatically: the
     * lean FINAL build whenever the frame's uploads match its baked-in
     * constants, and the diagnostic monolith otherwise. A non-null value pins
     * one program so a controlled same-fixture comparison can render the old
     * monolithic FINAL and the new lean FINAL from one run. Never set outside a
     * deliberate capture; {@link #resetDefaults()} clears it.
     */
    /**
     * T162 descriptor-count scaling arm. Negative means no limit. A positive
     * value caps the StormLobeCount the shader is told about, so a fixed-work
     * arm evaluates fewer descriptors per sample while everything else - pose,
     * target, sample count, fixture - is held identical. It changes what is
     * drawn, so it is only meaningful for the fixed-work ladder, where the
     * number of evaluations does not depend on what the storm looks like.
     */
    private static volatile int descriptorCountLimit = -1;
    private static volatile CoreCostDiagnosticProgram finalProgramOverride;
    /**
     * T188. Enables the rain-support field on the DIAGNOSTIC_MONOLITH.
     *
     * <p>Workload counters can only be read from the monolith, because every
     * lean program bakes DebugView to a constant. The monolith is not a field
     * program, so without this the ray-side fetch count and the post-field
     * attribution both read zero - not because the field did nothing, but
     * because the only program that can count was not using it.
     *
     * <p>Scoped to the monolith deliberately. Applying it to the timed arms
     * would make an anchor pay for a generation pass it does not run.
     */
    private static volatile boolean rainFieldForcedOnMonolith;
    private static volatile boolean depthCompositeEnabled = true;

    public static boolean rainFieldForcedOnMonolith() {
        return rainFieldForcedOnMonolith;
    }

    public static void setRainFieldForcedOnMonolith(boolean forced) {
        rainFieldForcedOnMonolith = forced;
    }

    /**
     * T203. The rain gate field on the monolith, on the same reasoning as the
     * rain field above: the counters that census the gate and the oracle
     * that checks its proofs only exist on the monolith, so it must build
     * and read the gate for them to describe anything. Never applied to a
     * timed arm.
     */
    private static volatile boolean rainGateForcedOnMonolith;

    public static boolean rainGateForcedOnMonolith() {
        return rainGateForcedOnMonolith;
    }

    public static void setRainGateForcedOnMonolith(boolean forced) {
        rainGateForcedOnMonolith = forced;
    }

    /**
     * T196. The shared storm field's consumer bits on the monolith, on T188's
     * reasoning: the monolith is the only program that counts, so it has to
     * read the field for the counters to describe the post-field workload -
     * the collapsed light and probe walks, the fetches, and the probe audit.
     * Bits as PaStormFieldEnabled: 1 light, 2 probe, 8 exact-noise tap,
     * 32 the refinement's clearance floor (T202); 16, the primary body from
     * the field, is a campaign arm only. Never applied to a timed arm.
     *
     * <p>T199. FINAL bakes {@link StormFieldGeometry#PRODUCTION_CONSUMER_BITS},
     * so the monolith defaults to the same value: a debug view renders the
     * image FINAL renders. A campaign that needs the monolith on another
     * lookup - or on none, 0 - sets it explicitly and restores production
     * with {@link #resetStormFieldMonolithBits()} when it retires.
     */
    private static volatile int stormFieldForcedOnMonolith =
            StormFieldGeometry.PRODUCTION_CONSUMER_BITS;

    /**
     * T196. One frame's soundness oracle: the next field build writes the
     * node floor beside a dense reference floor and the renderer reads the
     * pair back and reports it, then the ordinary build runs.
     */
    private static volatile boolean stormFieldSoundnessRequested;

    public static int stormFieldForcedOnMonolith() {
        return stormFieldForcedOnMonolith;
    }

    /** Campaign override; 0 means the monolith neither builds nor reads the field. */
    public static void setStormFieldForcedOnMonolith(int enabledBits) {
        stormFieldForcedOnMonolith = enabledBits;
    }

    /** T199. Back to production's consumers, and to lean-FINAL eligibility. */
    public static void resetStormFieldMonolithBits() {
        stormFieldForcedOnMonolith = StormFieldGeometry.PRODUCTION_CONSUMER_BITS;
    }

    public static void requestStormFieldSoundness() {
        stormFieldSoundnessRequested = true;
    }

    public static boolean consumeStormFieldSoundnessRequest() {
        boolean requested = stormFieldSoundnessRequested;
        stormFieldSoundnessRequested = false;
        return requested;
    }

    /**
     * T198. The light-tap audit's selectors, uploaded every frame: which
     * decomposition variant the exact side evaluates (0-7) and which
     * accumulator the audit program writes (0-7). Read only by
     * t198_light_audit.
     */
    private static volatile int lightAuditVariant;
    private static volatile int lightAuditOutput;

    public static int lightAuditVariant() {
        return lightAuditVariant;
    }

    public static int lightAuditOutput() {
        return lightAuditOutput;
    }

    public static void setLightAudit(int variant, int output) {
        lightAuditVariant = Math.max(0, Math.min(7, variant));
        lightAuditOutput = Math.max(0, Math.min(7, output));
    }

    /**
     * T198. Which light lookup the audit program measures: 1 the T196 node
     * density, 9 the exact-noise lookup. The audit program keeps the consumer
     * bits live so one program serves both.
     */
    private static volatile int lightAuditFieldBits = 1;

    public static int lightAuditFieldBits() {
        return lightAuditFieldBits;
    }

    public static void setLightAuditFieldBits(int bits) {
        lightAuditFieldBits = bits;
    }
    private static volatile boolean sceneRayLimitEnabled = true;
    // Uniform ray probes can miss narrow world-space cloud footprints and cut
    // complete horizontal bands from an otherwise valid volume. Keep the A/B
    // diagnostic available, but default to the correctness-preserving path
    // until a conservative occupancy structure replaces the probe heuristic.
    private static volatile boolean coveragePretestEnabled = false;
    private static volatile boolean adaptiveWeatherFootprintEnabled = true;
    // The structured PUFF height-map experiment is diagnostic-only: at the
    // current map resolution it creates stepped alpha fins. Production keeps
    // the stable macro path until that representation is redesigned.
    private static volatile boolean structuredPuffEnabled = false;
    // Keep the current production selection as the default while allowing an
    // explicit direct-only source for causal A/B. This selects one density
    // source inside the native renderer; it is unrelated to backend ownership.
    private static volatile VolumetricPuffShapeMode puffShapeMode = VolumetricPuffShapeMode.HYBRID;
    private static volatile VolumetricPuffDensityStage puffDensityStage = VolumetricPuffDensityStage.FINAL;
    private static volatile VolumetricPuffTierFilter puffTierFilter = VolumetricPuffTierFilter.ALL;
    private static volatile boolean historyEnabled = true;
    /**
     * T207. Temporal lattice reconstruction. 0 off (one raw frame per frame,
     * the T206 production path); 1 the four-phase lattice with coverage and
     * depth validity; 2 the same plus the colour clip; 3 the current phase
     * alone resolved through the lattice pass (the no-history baseline);
     * 4 trust-all (diagnostic, ghosts by construction); 5 mode 1 without the
     * T208 near-field guard and the T210 along-ray guard (T207's validity,
     * the control); 6 mode 1 without the along-ray guard (T208's production). The production
     * default is {@link #PRODUCTION_TEMPORAL_MODE}; a campaign sets its arm.
     * T207 banked mode 1: the resolve costs 0.1 ms, the lattice reaches
     * 0.995 SSIM against production 0.50 at PLAY_VIS_NEAR (0.951 at SIDE,
     * parity with the single frame, ahead on every structural term), and
     * the moving-camera route keeps the mean ghost at 0.0007 with nothing
     * older than the four-frame cycle. T208 added the near-field guard to
     * mode 1 (in-cloud history within 96 blocks, under a texel or more of
     * reprojection motion, at an ambiguous interval, is refused: the route's
     * frames above 0.02 mean alpha error 14 -> 5). Set to 0 to ship the
     * one-frame T206 path.
     */
    public static final int PRODUCTION_TEMPORAL_MODE = 1;
    private static volatile int temporalMode = PRODUCTION_TEMPORAL_MODE;
    /** T207. 1 makes the resolve write its acceptance class map instead of clouds; T208: 2 the body alone. */
    private static volatile int temporalDebugOutput;
    /**
     * T208 attribution. True skips the rain pass and clears its target to
     * "no rain" (T_r = 1), so every consumer - the split cloud program, the
     * resolve - sees a rainless frame. Diagnostic only.
     */
    private static volatile boolean rainDisabled;
    /**
     * T208 attribution. True uploads PaTemporalPhase = 1 on a non-temporal
     * frame, so the cloud target holds the body alone (the split deferred
     * and never composited) - the body-only reference. Diagnostic only.
     */
    private static volatile boolean deferredSplitForced;
    /**
     * T209. The spatial upscale of the temporal lattice to the display
     * (cloud_spatial_upscale.fsh): -1 off - the composite reads the lattice
     * directly, as T207 shipped it; 0 the PA bilinear moved into its own pass
     * (the control); 1 EASU on the premultiplied field; 2 depth-guided
     * Catmull-Rom. The production default is {@link #PRODUCTION_SPATIAL_UPSCALE};
     * a campaign sets its arm.
     */
    public static final int PRODUCTION_SPATIAL_UPSCALE = -1;
    /**
     * T210. Where the current frame's rain is evaluated on a temporal frame:
     * 0 the rain pass at the phase grid, its texel replicated over the 2x2
     * lattice block (production before T210); 1 the rain pass at the lattice
     * grid (twice the phase grid, every lattice pixel its own ray); 2 the
     * phase-grid pass first, then the lattice pass masked by it (a lattice
     * pixel whose 3x3 coarse neighbourhood is dry never marches). Rain stays
     * current-frame in every mode. The production default is
     * {@link #PRODUCTION_RAIN_LATTICE_MODE}.
     */
    public static final int PRODUCTION_RAIN_LATTICE_MODE = 0;
    private static volatile int rainLatticeMode = PRODUCTION_RAIN_LATTICE_MODE;
    /**
     * T211 defect 2. How far the blue-noise phase moves an exterior ray's
     * origin: 0 one {@code fineStep} (production before T211), 1 one
     * {@code coarseStep}, the stride a low-density body is actually
     * integrated on. A camera inside the medium keeps its short, stable
     * origin in both modes. The production default is
     * {@link #PRODUCTION_ORIGIN_JITTER_MODE}.
     */
    public static final int PRODUCTION_ORIGIN_JITTER_MODE = 0;
    private static volatile int originJitterMode = PRODUCTION_ORIGIN_JITTER_MODE;
    /**
     * T212. Macro-scale modulation of the coverage envelope before the final
     * density threshold: 0 production, 1 on.
     *
     * <p>T212's attribution found the storm body is NOT over-filled at the
     * boundary - the measured per-point boundary displacement is already
     * +-33 blocks, and the rendered silhouette carries the ~12 px of non-
     * elliptical roughness that predicts. The defect is that the ellipse
     * harmonic is 2.3-3.6x larger than the macro band, and the base noise has
     * no energy long enough to fight it: its lowest octave is ~50 blocks,
     * which lands at harmonic ~28 on a 450-block anvil. This adds the missing
     * band - one Worley octave at ~400 blocks - to the coverage envelope
     * itself, where it displaces the surface coherently instead of speckling
     * the density.
     */
    public static final int PRODUCTION_MACRO_ENVELOPE_MODE = 3;

    /**
     * T215. Lower-body fade reinforcement: 0 production, 1 on.
     *
     * <p>T215's attribution found the lower mass is solid in its core and
     * perforates through the BASE lobe's envelope FADE - 141-145 blocks wide,
     * the widest of any role, covering roughly half that lobe's projected
     * area. Measured radial occupancy at SIDE falls 1.000 / 0.985 / 0.933 /
     * 0.667 / 0.459 / 0.103 from centre to rim, with the break starting where
     * the fade starts. Core fill is NOT the cause: `coverage*(1+fill)` is
     * 1.397 at BASE against 1.396 at ANVIL, so the body-versus-noise response
     * is the same function in both. This lifts coverage in the MIDDLE of the
     * fade, where the speckle is, and leaves both the core and the far fringe
     * alone so the silhouette is not simply inflated.
     */
    public static final int PRODUCTION_LOWER_BODY_MODE = 1;
    private static volatile int lowerBodyMode = PRODUCTION_LOWER_BODY_MODE;
    /**
     * T217 deep-interior indirect light. 1 is production: a bounded
     * multiple-scattering fill gated on camera embedding. 0 disables it.
     */
    public static final int PRODUCTION_DEEP_SCATTER_MODE = 1;
    private static volatile int deepScatterMode = PRODUCTION_DEEP_SCATTER_MODE;
    private static volatile int macroEnvelopeMode = PRODUCTION_MACRO_ENVELOPE_MODE;
    /**
     * T210 harness. True raises MaxPrecipitation to 0.85 on every program
     * (T188's rain-heavy forcing on the deterministic storm), identically for
     * the reference and the arms - a deliberately rain-heavy fixture.
     */
    private static volatile boolean rainHeavyForced;
    private static volatile int spatialUpscaleMethod = PRODUCTION_SPATIAL_UPSCALE;
    /**
     * Rank 1 reconstruction arm. False keeps the shipped composite accumulation
     * unchanged; true selects the coverage-alpha reconstruction, which removes
     * the resolution-scale beat by separating the silhouette's coverage from
     * the depth-aware colour accumulation. Diagnostic until T138 promotes it.
     */
    private static volatile boolean coverageAlphaReconstruction;
    private static volatile VolumetricCloudRaymarchDebugView raymarchDebugView =
            VolumetricCloudRaymarchDebugView.FINAL;
    // Fixed empty-map base/top sentinels bleed into cloud fringes through the
    // weather map's linear filter. Keep the legacy behaviour as an A/B switch,
    // but use real fringe heights in the production path.
    private static volatile boolean sentinelHeightsEnabled;
    private static volatile boolean fullResolutionEnabled;
    // Diagnostic-only target override. Normal rendering leaves this NaN and
    // continues to use the adaptive dense-camera policy unchanged.
    private static volatile float fixedResolutionScale = Float.NaN;
    private static volatile float weatherCoverageScale = 1.0F;
    private static volatile int coveragePretestSamples = 6;
    private static volatile float coveragePretestThreshold = 0.004F;
    private static volatile int coveragePretestDilation;
    private static volatile StormTopologyMode stormTopologyMode = StormTopologyMode.COMPACT;
    /**
     * T133 / SC-020. Never diagnostic outside a deliberate capture: the suite
     * sets an OFF arm and restores this before the view ends, and
     * {@link #resetDefaults()} restores it on every session/world transition.
     */
    private static volatile StormOptimizationDiagnosticMode optimizationDiagnosticMode =
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION;

    private VolumetricCloudDebugConfig() {
    }

    public static boolean t136ConstantLighting() {
        return t136ConstantLighting;
    }

    public static void setT136ConstantLighting(boolean enabled) {
        t136ConstantLighting = enabled;
    }

    /** The value uploaded as PaDiagnosticLightingMode. */
    public static int diagnosticLightingMode() {
        return t136ConstantLighting ? 1 : diagnosticLightingMode;
    }

    public static void setDiagnosticLightingMode(int mode) {
        diagnosticLightingMode = Math.max(0, Math.min(18, mode));
    }

    public static boolean t098LegacyFinePromotion() {
        return t098LegacyFinePromotion;
    }

    public static void setT098LegacyFinePromotion(boolean enabled) {
        t098LegacyFinePromotion = enabled;
    }

    public static boolean t098LegacyHitDepth() {
        return t098LegacyHitDepth;
    }

    public static void setT098LegacyHitDepth(boolean enabled) {
        t098LegacyHitDepth = enabled;
    }

    public static boolean depthCompositeEnabled() {
        return depthCompositeEnabled;
    }

    public static void setDepthCompositeEnabled(boolean enabled) {
        depthCompositeEnabled = enabled;
    }

    public static boolean sceneRayLimitEnabled() {
        return sceneRayLimitEnabled;
    }

    public static void setSceneRayLimitEnabled(boolean enabled) {
        sceneRayLimitEnabled = enabled;
    }

    public static boolean fullResolutionEnabled() {
        return fullResolutionEnabled;
    }

    public static void setFullResolutionEnabled(boolean enabled) {
        fullResolutionEnabled = enabled;
    }

    public static float fixedResolutionScale() {
        return fixedResolutionScale;
    }

    /**
     * Diagnostic override for the cloud target's linear scale.
     *
     * <p>The floor matches {@code VolumetricCloudRenderTargets.prepareCloudTargets},
     * which is the only place the value is finally clamped. It was 0.25 here,
     * which silently pinned every more aggressive arm to a 480x270 target: the
     * Rank 1 sweep's 0.1875 and 0.125 cells reported their labels and rendered
     * a quarter-scale image. A diagnostic floor tighter than the renderer's own
     * makes the frontier it exists to explore unreachable.
     */
    public static void setFixedResolutionScale(float scale) {
        fixedResolutionScale = Float.isFinite(scale)
                ? Math.max(0.10F, Math.min(1.0F, scale))
                : Float.NaN;
    }

    public static boolean coveragePretestEnabled() {
        return coveragePretestEnabled;
    }

    public static void setCoveragePretestEnabled(boolean enabled) {
        coveragePretestEnabled = enabled;
    }

    public static boolean adaptiveWeatherFootprintEnabled() {
        return adaptiveWeatherFootprintEnabled;
    }

    public static void setAdaptiveWeatherFootprintEnabled(boolean enabled) {
        adaptiveWeatherFootprintEnabled = enabled;
    }

    public static boolean structuredPuffEnabled() {
        return structuredPuffEnabled;
    }

    public static void setStructuredPuffEnabled(boolean enabled) {
        structuredPuffEnabled = enabled;
    }

    public static boolean directPuffEnabled() {
        return puffShapeMode.usesDirectDescriptors();
    }

    public static void setDirectPuffEnabled(boolean enabled) {
        puffShapeMode = enabled
                ? VolumetricPuffShapeMode.HYBRID
                : VolumetricPuffShapeMode.FALLBACK_ONLY;
    }

    public static VolumetricPuffShapeMode puffShapeMode() {
        return puffShapeMode;
    }

    public static void setPuffShapeMode(VolumetricPuffShapeMode mode) {
        puffShapeMode = mode == null ? VolumetricPuffShapeMode.HYBRID : mode;
    }

    public static VolumetricPuffDensityStage puffDensityStage() {
        return puffDensityStage;
    }

    public static void setPuffDensityStage(VolumetricPuffDensityStage stage) {
        puffDensityStage = stage == null ? VolumetricPuffDensityStage.FINAL : stage;
    }

    public static VolumetricPuffTierFilter puffTierFilter() {
        return puffTierFilter;
    }

    public static void setPuffTierFilter(VolumetricPuffTierFilter filter) {
        puffTierFilter = filter == null ? VolumetricPuffTierFilter.ALL : filter;
    }

    public static boolean coverageAlphaReconstruction() {
        return coverageAlphaReconstruction;
    }

    public static void setCoverageAlphaReconstruction(boolean enabled) {
        coverageAlphaReconstruction = enabled;
    }

    public static boolean historyEnabled() {
        return historyEnabled;
    }

    public static int temporalMode() {
        return temporalMode;
    }

    public static void setTemporalMode(int mode) {
        temporalMode = Math.max(0, Math.min(6, mode));
    }

    public static int temporalDebugOutput() {
        return temporalDebugOutput;
    }

    public static void setTemporalDebugOutput(int output) {
        temporalDebugOutput = output;
    }

    public static boolean rainDisabled() {
        return rainDisabled;
    }

    /**
     * T231, production. The lower-anvil macro relief (T230's mirror of T213's
     * deck window) and the form shading that makes it visible: shadowed
     * ambient x (1 - strength x macroSigned x lowerWeight x shadow). Sagging
     * lobes lighter, hollows darker; no density or alpha effect of its own.
     */
    public static final int PRODUCTION_LOWER_DECK_RELIEF_MODE = 1;
    public static final float PRODUCTION_RELIEF_SHADE = 0.12F;
    private static volatile int lowerDeckReliefMode = PRODUCTION_LOWER_DECK_RELIEF_MODE;
    private static volatile float reliefShadeStrength = PRODUCTION_RELIEF_SHADE;

    public static int lowerDeckReliefMode() {
        return lowerDeckReliefMode;
    }

    public static void setLowerDeckReliefMode(int mode) {
        lowerDeckReliefMode = Math.max(0, Math.min(1, mode));
    }

    public static float reliefShadeStrength() {
        return reliefShadeStrength;
    }

    public static void setReliefShadeStrength(float strength) {
        reliefShadeStrength = Math.max(0.0F, strength);
    }

    /**
     * T225. Replaces the uploaded MaxPrecipitation (the frame-wide maximum)
     * when finite, for every program. The stationary rain ramp drives it; the
     * shader sees only this scalar, so it reproduces a remote cell raising the
     * global maximum while the local storm's cells stay as they are. NaN = off.
     */
    private static volatile float precipitationOverride = Float.NaN;

    public static float precipitationOverride() {
        return precipitationOverride;
    }

    public static void setPrecipitationOverride(float value) {
        precipitationOverride = value;
    }

    public static void setRainDisabled(boolean disabled) {
        rainDisabled = disabled;
    }

    public static boolean deferredSplitForced() {
        return deferredSplitForced;
    }

    public static int spatialUpscaleMethod() {
        return spatialUpscaleMethod;
    }

    public static int rainLatticeMode() {
        return rainLatticeMode;
    }

    public static void setRainLatticeMode(int mode) {
        rainLatticeMode = Math.max(0, Math.min(2, mode));
    }

    public static int originJitterMode() {
        return originJitterMode;
    }

    public static void setOriginJitterMode(int mode) {
        originJitterMode = Math.max(0, Math.min(2, mode));
    }

    public static int macroEnvelopeMode() {
        return macroEnvelopeMode;
    }

    public static void setMacroEnvelopeMode(int mode) {
        macroEnvelopeMode = Math.max(0, Math.min(5, mode));
    }

    public static int lowerBodyMode() {
        return lowerBodyMode;
    }

    public static int deepScatterMode() {
        return deepScatterMode;
    }

    public static void setDeepScatterMode(int mode) {
        deepScatterMode = Math.max(0, Math.min(1, mode));
    }

    public static void setLowerBodyMode(int mode) {
        lowerBodyMode = Math.max(0, Math.min(2, mode));
    }

    public static boolean rainHeavyForced() {
        return rainHeavyForced;
    }

    public static void setRainHeavyForced(boolean forced) {
        rainHeavyForced = forced;
    }

    public static void setSpatialUpscaleMethod(int method) {
        spatialUpscaleMethod = Math.max(-1, Math.min(2, method));
    }

    public static void setDeferredSplitForced(boolean forced) {
        deferredSplitForced = forced;
    }

    public static void setHistoryEnabled(boolean enabled) {
        historyEnabled = enabled;
    }

    public static VolumetricCloudRaymarchDebugView raymarchDebugView() {
        return raymarchDebugView;
    }

    public static void setRaymarchDebugView(VolumetricCloudRaymarchDebugView view) {
        raymarchDebugView = view == null
                ? VolumetricCloudRaymarchDebugView.FINAL
                : view;
    }

    public static boolean sentinelHeightsEnabled() {
        return sentinelHeightsEnabled;
    }

    public static void setSentinelHeightsEnabled(boolean enabled) {
        sentinelHeightsEnabled = enabled;
    }

    public static float weatherCoverageScale() {
        return weatherCoverageScale;
    }

    public static void setWeatherCoverageScale(float scale) {
        if (!Float.isFinite(scale)) {
            weatherCoverageScale = 1.0F;
            return;
        }
        weatherCoverageScale = Math.max(0.25F, Math.min(4.0F, scale));
    }

    public static int coveragePretestSamples() {
        return coveragePretestSamples;
    }

    public static void setCoveragePretestSamples(int samples) {
        coveragePretestSamples = Math.max(6, Math.min(16, samples));
    }

    public static float coveragePretestThreshold() {
        return coveragePretestThreshold;
    }

    public static void setCoveragePretestThreshold(float threshold) {
        if (!Float.isFinite(threshold)) {
            coveragePretestThreshold = 0.004F;
            return;
        }
        coveragePretestThreshold = Math.max(0.0F, Math.min(0.05F, threshold));
    }

    public static int coveragePretestDilation() {
        return coveragePretestDilation;
    }

    public static void setCoveragePretestDilation(int dilation) {
        coveragePretestDilation = Math.max(0, Math.min(2, dilation));
    }

    public static StormTopologyMode stormTopologyMode() {
        return stormTopologyMode;
    }

    public static void setStormTopologyMode(StormTopologyMode mode) {
        stormTopologyMode = mode == null ? StormTopologyMode.COMPACT : mode;
    }

    public static StormOptimizationDiagnosticMode optimizationDiagnosticMode() {
        return optimizationDiagnosticMode;
    }

    public static void setOptimizationDiagnosticMode(StormOptimizationDiagnosticMode mode) {
        optimizationDiagnosticMode =
                mode == null ? StormOptimizationDiagnosticMode.NORMAL_PRODUCTION : mode;
    }

    /** Negative when the shader sees the true resident descriptor count. */
    public static int descriptorCountLimit() {
        return descriptorCountLimit;
    }

    public static void setDescriptorCountLimit(int limit) {
        descriptorCountLimit = limit;
    }

    /** Null when the renderer selects the FINAL program automatically. */
    public static CoreCostDiagnosticProgram finalProgramOverride() {
        return finalProgramOverride;
    }

    public static void setFinalProgramOverride(CoreCostDiagnosticProgram program) {
        finalProgramOverride = program;
    }

    /**
     * Returns every comparison switch to the production baseline. Debug state
     * is process-static, so session/world/backend transitions must call this
     * explicitly or one diagnostic can silently contaminate a later world.
     */
    public static void resetDefaults() {
        depthCompositeEnabled = true;
        sceneRayLimitEnabled = true;
        coveragePretestEnabled = false;
        adaptiveWeatherFootprintEnabled = true;
        structuredPuffEnabled = false;
        puffShapeMode = VolumetricPuffShapeMode.HYBRID;
        puffDensityStage = VolumetricPuffDensityStage.FINAL;
        puffTierFilter = VolumetricPuffTierFilter.ALL;
        historyEnabled = true;
        temporalMode = PRODUCTION_TEMPORAL_MODE;
        temporalDebugOutput = 0;
        rainDisabled = false;
        deferredSplitForced = false;
        spatialUpscaleMethod = PRODUCTION_SPATIAL_UPSCALE;
        rainLatticeMode = PRODUCTION_RAIN_LATTICE_MODE;
        originJitterMode = PRODUCTION_ORIGIN_JITTER_MODE;
        macroEnvelopeMode = PRODUCTION_MACRO_ENVELOPE_MODE;
        lowerBodyMode = PRODUCTION_LOWER_BODY_MODE;
        deepScatterMode = PRODUCTION_DEEP_SCATTER_MODE;
        rainHeavyForced = false;
        precipitationOverride = Float.NaN;
        lowerDeckReliefMode = PRODUCTION_LOWER_DECK_RELIEF_MODE;
        reliefShadeStrength = PRODUCTION_RELIEF_SHADE;
        raymarchDebugView = VolumetricCloudRaymarchDebugView.FINAL;
        sentinelHeightsEnabled = false;
        fullResolutionEnabled = false;
        fixedResolutionScale = Float.NaN;
        weatherCoverageScale = 1.0F;
        coveragePretestSamples = 6;
        coveragePretestThreshold = 0.004F;
        coveragePretestDilation = 0;
        stormTopologyMode = StormTopologyMode.COMPACT;
        optimizationDiagnosticMode = StormOptimizationDiagnosticMode.NORMAL_PRODUCTION;
        finalProgramOverride = null;
        descriptorCountLimit = -1;
    }

    /** Pure reset-contract check for the standalone renderer sandbox. */
    public static void selfCheck() {
        depthCompositeEnabled = false;
        sceneRayLimitEnabled = false;
        coveragePretestEnabled = true;
        adaptiveWeatherFootprintEnabled = false;
        structuredPuffEnabled = true;
        puffShapeMode = VolumetricPuffShapeMode.FALLBACK_ONLY;
        puffDensityStage = VolumetricPuffDensityStage.ANALYTIC_ALL;
        puffTierFilter = VolumetricPuffTierFilter.CROWN;
        historyEnabled = false;
        raymarchDebugView = VolumetricCloudRaymarchDebugView.CURRENT_ONLY;
        sentinelHeightsEnabled = true;
        fullResolutionEnabled = true;
        fixedResolutionScale = 0.75F;
        weatherCoverageScale = 2.0F;
        coveragePretestSamples = 12;
        coveragePretestThreshold = 0.02F;
        coveragePretestDilation = 2;
        stormTopologyMode = StormTopologyMode.LEGACY_SCAN;
        finalProgramOverride = CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH;
        descriptorCountLimit = 4;
        resetDefaults();
        if (!depthCompositeEnabled
                || !sceneRayLimitEnabled
                || coveragePretestEnabled
                || !adaptiveWeatherFootprintEnabled
                || structuredPuffEnabled
                || puffShapeMode != VolumetricPuffShapeMode.HYBRID
                || puffDensityStage != VolumetricPuffDensityStage.FINAL
                || puffTierFilter != VolumetricPuffTierFilter.ALL
                || !historyEnabled
                || raymarchDebugView != VolumetricCloudRaymarchDebugView.FINAL
                || sentinelHeightsEnabled
                || fullResolutionEnabled
                || Float.isFinite(fixedResolutionScale)
                || weatherCoverageScale != 1.0F
                || coveragePretestSamples != 6
                || coveragePretestThreshold != 0.004F
                || coveragePretestDilation != 0
                || stormTopologyMode != StormTopologyMode.COMPACT
                || finalProgramOverride != null
                || descriptorCountLimit != -1) {
            throw new IllegalStateException("volumetric debug defaults did not reset exactly");
        }
    }

    public static String status() {
        return "depthComposite=" + (depthCompositeEnabled ? "on" : "off")
                + "\nsceneRayLimit=" + (sceneRayLimitEnabled ? "on" : "off")
                + "\ncoveragePretest=" + (coveragePretestEnabled ? "on" : "off")
                + "\ncoveragePretestSamples=" + coveragePretestSamples
                + "\ncoveragePretestThreshold=" + coveragePretestThreshold
                + "\ncoveragePretestDilation=" + coveragePretestDilation
                + "\nadaptiveWeatherFootprint=" + (adaptiveWeatherFootprintEnabled ? "on" : "off")
                + "\nstructuredPuff=" + (structuredPuffEnabled ? "on" : "off")
                + "\npuffShape=" + puffShapeMode.serializedName()
                + "\npuffDensity=" + puffDensityStage.serializedName()
                + "\npuffTier=" + puffTierFilter.serializedName()
                + "\nhistory=" + (historyEnabled ? "on" : "off")
                + "\nraymarchView=" + raymarchDebugView.serializedName()
                + "\nsentinelHeights=" + (sentinelHeightsEnabled ? "on" : "off")
                + "\nfullres=" + (fullResolutionEnabled ? "on" : "off")
                + "\nweatherCoverageScale=" + weatherCoverageScale
                + "\nstormTopology=" + stormTopologyMode.serializedName()
                + "\nfinalProgram=" + (finalProgramOverride == null
                        ? "auto"
                        : finalProgramOverride.serializedName());
    }
}

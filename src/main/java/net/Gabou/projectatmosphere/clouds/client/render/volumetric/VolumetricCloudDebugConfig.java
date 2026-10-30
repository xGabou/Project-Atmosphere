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

    /** T240. The storm geometry FINAL bakes: the continuous storm body. */
    public static final int PRODUCTION_STORM_GEOMETRY = 1;
    /**
     * T240. The macro layering FINAL bakes on the continuous body: 2, the
     * T213 envelope and the T230 lower-deck relief - production's existing
     * large-scale variation, which also drives T231's relief shading.
     */
    public static final int PRODUCTION_BODY_MACRO = 2;
    /** The storm geometry uploaded as PaStormGeometry (monolith A/B only). */
    private static volatile int stormGeometryMode = PRODUCTION_STORM_GEOMETRY;
    /** PaBodyMacro: 0 none, 1 T213 macro envelope, 2 envelope + T230 lower-deck relief. */
    private static volatile int bodyMacroMode = PRODUCTION_BODY_MACRO;

    public static int bodyMacroMode() {
        return bodyMacroMode;
    }

    public static void setBodyMacroMode(int mode) {
        bodyMacroMode = Math.max(0, Math.min(2, mode));
    }

    /**
     * T240. PaStormGeometry: 0 the legacy role-lobe union (monolith A/B only),
     * 1 the continuous storm body (production). Any value but production takes
     * the monolith.
     */
    public static int stormGeometryMode() {
        return stormGeometryMode;
    }

    public static void setStormGeometryMode(int mode) {
        stormGeometryMode = mode;
    }

    public static void setDiagnosticLightingMode(int mode) {
        // L-TEMP: 40 / 41 (stabilisation lighting investigation) - the upper bound was 18.
        diagnosticLightingMode = Math.max(0, Math.min(41, mode));
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

    /**
     * S1 (stabilisation, RC-01): 0 legacy (Minecraft colours used as radiance, per-sample display curve in the
     * march), 1 colour-managed (linearised inputs, linear radiance, one curve + sRGB encode in the composite).
     * The march (PaColorPipeline) and the composite (ColorPipeline) always receive the same value.
     */
    public static final int PRODUCTION_COLOR_PIPELINE = 1; // E16: user accepted the s10 full_g2 gallery
    private static volatile int colorPipeline = PRODUCTION_COLOR_PIPELINE;
    /** S2 (stabilisation, RC-08): 1 legacy (Hillaire octaves normalised), 0 the octaves add their energy. */
    public static final int PRODUCTION_OCTAVE_NORMALIZE = 0; // E16
    private static volatile int octaveNormalize = PRODUCTION_OCTAVE_NORMALIZE;

    public static int colorPipeline() {
        return colorPipeline;
    }

    public static void setColorPipeline(int mode) {
        colorPipeline = Math.max(0, Math.min(1, mode));
    }

    public static int octaveNormalize() {
        return octaveNormalize;
    }

    public static void setOctaveNormalize(int mode) {
        octaveNormalize = Math.max(0, Math.min(1, mode));
    }

    /** S1b (stabilisation, RC-21): 0 legacy ambient retention floor, 1 diffuse two-stream transmission of the light path. */
    public static final int PRODUCTION_AMBIENT_TRANSMISSION = 1; // E16
    private static volatile int ambientTransmission = PRODUCTION_AMBIENT_TRANSMISSION;

    public static int ambientTransmission() {
        return ambientTransmission;
    }

    public static void setAmbientTransmission(int mode) {
        ambientTransmission = Math.max(0, Math.min(1, mode));
    }

    /** S9 (stabilisation, shape root cause): amplitude of the cumulus billow hierarchy, 0 = legacy form. */
    public static final float PRODUCTION_SHAPE_HIERARCHY = 0.0F;
    private static volatile float shapeHierarchy = PRODUCTION_SHAPE_HIERARCHY;

    public static float shapeHierarchy() {
        return shapeHierarchy;
    }

    public static void setShapeHierarchy(float amplitude) {
        shapeHierarchy = Math.max(0.0F, Math.min(4.0F, amplitude));
    }

    /** E10 (stabilisation, Codex CR-05): 1 = exact covered/uncovered test for field light taps (no fabricated boxes). */
    public static final int PRODUCTION_FIELD_COVERAGE_FIX = 1; // E11: verified in game; correctness fix
    private static volatile int fieldCoverageFix = PRODUCTION_FIELD_COVERAGE_FIX;

    public static int fieldCoverageFix() {
        return fieldCoverageFix;
    }

    public static void setFieldCoverageFix(int mode) {
        fieldCoverageFix = Math.max(0, Math.min(1, mode));
    }

    /** E12 (stabilisation): light-path optical-thickness gain for dark / thick families, 0 = off. */
    public static final float PRODUCTION_OPTICAL_THICKNESS_GAIN = 2.0F; // E16 (gain 2 chosen over 4)
    private static volatile float opticalThicknessGain = PRODUCTION_OPTICAL_THICKNESS_GAIN;

    public static float opticalThicknessGain() {
        return opticalThicknessGain;
    }

    public static void setOpticalThicknessGain(float gain) {
        opticalThicknessGain = Math.max(0.0F, Math.min(40.0F, gain));
    }

    /** E13 (stabilisation): descriptor-storm material from the storm walk instead of the empty map; 0 = legacy. */
    public static final int PRODUCTION_STORM_MATERIAL_FIX = 2; // E16; 2 = E20 continuous storm lighting height (27 Oct)
    private static volatile int stormMaterialFix = PRODUCTION_STORM_MATERIAL_FIX;

    public static int stormMaterialFix() {
        return stormMaterialFix;
    }

    public static void setStormMaterialFix(int mode) {
        stormMaterialFix = Math.max(0, Math.min(2, mode)); // 2: E20 continuous storm lighting height
    }

    /** S25-TEMP (cumulus rings): 1 = nearest light tap of non-storm samples on the shared field (pre-T247). */
    private static volatile int lightTap0Field = 0;

    public static int lightTap0Field() {
        return lightTap0Field;
    }

    public static void setLightTap0Field(int mode) {
        lightTap0Field = mode != 0 ? 1 : 0;
    }

    /**
     * S26-TEMP (one-body cumulus): mode 0 = lobe union, 1 = one body (metaball sum of lobe bumps); bump support in
     * lobe radii, surface threshold, shell density, field rise to full density.
     */
    private static volatile int puffBody = 0;
    private static volatile float puffBodyK = 1.15F;
    private static volatile float puffBodyThreshold = 0.09F;
    private static volatile float puffBodyRelief = 1.0F;
    private static volatile float puffBodyTurrets = 0.0F;
    private static volatile float puffBodyErode = 0.0F;
    private static volatile float puffBodyShell = 0.45F;
    private static volatile float puffBodyCoreDepth = 0.6F;

    public static int puffBody() {
        return puffBody;
    }

    public static float puffBodyK() {
        return puffBodyK;
    }

    public static float puffBodyThreshold() {
        return puffBodyThreshold;
    }

    public static float puffBodyRelief() {
        return puffBodyRelief;
    }

    public static float puffBodyTurrets() {
        return puffBodyTurrets;
    }

    public static float puffBodyErode() {
        return puffBodyErode;
    }

    public static void setPuffBodyDetail(float turrets, float erode) {
        puffBodyTurrets = Math.max(0.0F, Math.min(3.0F, turrets));
        puffBodyErode = Math.max(0.0F, Math.min(2.0F, erode));
    }

    public static void setPuffBodyRelief(float relief) {
        puffBodyRelief = Math.max(0.0F, Math.min(4.0F, relief));
    }

    public static float puffBodyShell() {
        return puffBodyShell;
    }

    public static float puffBodyCoreDepth() {
        return puffBodyCoreDepth;
    }

    public static void setPuffBody(int mode, float support, float threshold, float shell, float coreDepth) {
        puffBody = mode != 0 ? 1 : 0;
        puffBodyK = Math.max(1.0F, Math.min(1.25F, support)); // 1.25 = the tile footprint padding (PUFF_WARP_MAX)
        puffBodyThreshold = Math.max(0.005F, Math.min(0.9F, threshold));
        puffBodyShell = Math.max(0.0F, Math.min(1.0F, shell));
        puffBodyCoreDepth = Math.max(0.05F, coreDepth);
    }

    /**
     * E33 near-field light: exact light samples in the first 12 blocks toward the sun (0 - 3). Production follows
     * the player's Cloud Surface Detail setting (AtmoCommonConfig.CLOUD_SURFACE_DETAIL); a capture arm overrides
     * it (-1 = none).
     */
    public static final int PRODUCTION_NEAR_LIGHT = -1;
    private static volatile int nearLight = PRODUCTION_NEAR_LIGHT;

    public static int nearLight() {
        return nearLight >= 0 ? nearLight
                : net.Gabou.projectatmosphere.config.AtmoCommonConfig.CLOUD_SURFACE_DETAIL.get().nearLightSamples();
    }

    public static void setNearLight(int samples) {
        nearLight = Math.max(-1, Math.min(3, samples));
    }

    /** S32-TEMP (grain): fixed surface-bracket integration phase; < 0 = production blue noise. */
    private static volatile float integrationPhase = -1.0F;

    public static float integrationPhase() {
        return integrationPhase;
    }

    public static void setIntegrationPhase(float phase) {
        integrationPhase = phase < 0.0F ? -1.0F : Math.min(2.0F, phase);
    }

    /**
     * E39 per-phase accumulation (the temporal lattice's missing average): 0 off (T207 raw phases), 1 on.
     * Minimum blend weight (1 / max samples averaged) and the material drift (blocks) beyond which a phase is
     * replaced instead of averaged.
     */
    // E39 (28 Oct 2026, user-approved "the one that fixes it really"): on. s45: the frozen per-pixel grain is
    // gone at identical cost; a static camera converges over 8 renders per phase (32 frames).
    public static final int PRODUCTION_PHASE_ACCUMULATE = 1;
    public static final float PRODUCTION_PHASE_ACCUMULATE_MIN_WEIGHT = 0.125F;
    public static final float PRODUCTION_PHASE_ACCUMULATE_MAX_DRIFT = 2.0F;
    private static volatile int phaseAccumulate = PRODUCTION_PHASE_ACCUMULATE;
    private static volatile float phaseAccumulateMinWeight = PRODUCTION_PHASE_ACCUMULATE_MIN_WEIGHT;
    private static volatile float phaseAccumulateMaxDrift = PRODUCTION_PHASE_ACCUMULATE_MAX_DRIFT;

    public static int phaseAccumulate() {
        return phaseAccumulate;
    }

    public static float phaseAccumulateMinWeight() {
        return phaseAccumulateMinWeight;
    }

    public static float phaseAccumulateMaxDrift() {
        return phaseAccumulateMaxDrift;
    }

    public static void setPhaseAccumulate(int mode, float minWeight, float maxDrift) {
        phaseAccumulate = mode != 0 ? 1 : 0;
        phaseAccumulateMinWeight = Math.max(0.02F, Math.min(1.0F, minWeight));
        phaseAccumulateMaxDrift = Math.max(0.0F, maxDrift);
    }

    /**
     * E41 motion history in the temporal resolve: the weight last frame's (reprojected, clamped) output keeps
     * while the camera moves; 0 = off (the T207 + E39 path at every frame). A static camera never uses it.
     */
    public static final float PRODUCTION_MOTION_HISTORY_WEIGHT = 0.9F; // E41 s46: beats the pre-E39 grain on slow and fast pans
    private static volatile float motionHistoryWeight = PRODUCTION_MOTION_HISTORY_WEIGHT;

    public static float motionHistoryWeight() {
        return motionHistoryWeight;
    }

    public static void setMotionHistoryWeight(float weight) {
        motionHistoryWeight = Math.max(0.0F, Math.min(0.95F, weight));
    }

    /** S33-TEMP (cauliflower): lobe-surface bubble crease strength (0 = production). */
    private static volatile float puffBubbles = 0.0F;

    public static float puffBubbles() {
        return puffBubbles;
    }

    public static void setPuffBubbles(float strength) {
        puffBubbles = Math.max(0.0F, Math.min(3.0F, strength));
    }

    private static volatile float puffBubbleSharp = 0.0F;

    public static float puffBubbleSharp() {
        return puffBubbleSharp;
    }

    public static void setPuffBubbleSharp(float sharp) {
        puffBubbleSharp = sharp;
    }

    /** S31-TEMP (grain): detail noise filtered to this x the march step (0 = production). */
    private static volatile float detailStepFilter = 0.0F;

    public static float detailStepFilter() {
        return detailStepFilter;
    }

    public static void setDetailStepFilter(float factor) {
        detailStepFilter = Math.max(0.0F, Math.min(2.0F, factor));
    }

    /** S30-TEMP (grain): multiplier on the exterior fine step (1 = production). */
    private static volatile float fineStepMul = 1.0F;

    public static float fineStepMul() {
        return fineStepMul;
    }

    public static void setFineStepMul(float mul) {
        fineStepMul = Math.max(0.25F, Math.min(2.0F, mul));
    }

    /** S29-TEMP (flat surfaces): multiplier on the global ExtinctionScale (1 = production). */
    private static volatile float extinctionMul = 1.0F;

    public static float extinctionMul() {
        return extinctionMul;
    }

    public static void setExtinctionMul(float mul) {
        extinctionMul = Math.max(0.25F, Math.min(8.0F, mul));
    }

    /** S27-TEMP (flat surfaces): 1 sun only, 2 ambient only, 3 one octave, 4 no powder; 0 = production. */
    private static volatile int lightSplit = 0;

    public static int lightSplit() {
        return lightSplit;
    }

    public static void setLightSplit(int mode) {
        lightSplit = Math.max(0, Math.min(4, mode));
    }

    /** S13 (stabilisation, cumulus shape): smooth-max width of the two strongest puff lobes; 0 = legacy crease. */
    public static final float PRODUCTION_PUFF_BLEND = 0.0F;
    private static volatile float puffBlend = PRODUCTION_PUFF_BLEND;

    public static float puffBlend() {
        return puffBlend;
    }

    public static void setPuffBlend(float k) {
        puffBlend = Math.max(0.0F, Math.min(1.0F, k));
    }

    /** S12 (stabilisation, cumulus shape): puff lobe warp amplitude in lobe radii; 0 = legacy. */
    public static final float PRODUCTION_PUFF_WARP = 0.0F;
    private static volatile float puffWarp = PRODUCTION_PUFF_WARP;

    public static float puffWarp() {
        return puffWarp;
    }

    public static void setPuffWarp(float amplitude) {
        puffWarp = Math.max(0.0F, Math.min(0.25F, amplitude));
    }

    /**
     * L5 (stabilisation lighting resolution): light taps i < N read exact density over storms. Production follows
     * the player's menu setting (AtmoCommonConfig.STORM_SHADOW_DETAIL); a capture arm overrides it (-1 = none).
     */
    public static final int PRODUCTION_EXACT_LIGHT_TAPS = -1;
    private static volatile int exactLightTaps = PRODUCTION_EXACT_LIGHT_TAPS;

    public static int exactLightTaps() {
        return exactLightTaps >= 0 ? exactLightTaps
                : net.Gabou.projectatmosphere.config.AtmoCommonConfig.STORM_SHADOW_DETAIL.get().exactLightTaps();
    }

    public static void setExactLightTaps(int taps) {
        exactLightTaps = Math.max(-1, Math.min(8, taps));
    }

    /** P3 (stabilisation perf): 1 = the empty-space jump over clear 64-block weather tiles; 0 = legacy. */
    public static final int PRODUCTION_EMPTY_SKIP = 1; // P3: empty view -0.9 ms, storm / cumulus neutral (p7, per-ray storm span)
    private static volatile int emptySkip = PRODUCTION_EMPTY_SKIP;

    public static int emptySkip() {
        return emptySkip;
    }

    public static void setEmptySkip(int mode) {
        emptySkip = Math.max(0, Math.min(1, mode));
    }

    /** P2 (stabilisation perf): the field atlas rebuilt in 1 (legacy, whole), 2 or 4 bands per frame. */
    public static final int PRODUCTION_FIELD_SLICES = 4; // P2: p6 field 1.37->0.49 ms (storm), 0.62->0.19 (cumulus); audit 0
    private static volatile int fieldSlices = PRODUCTION_FIELD_SLICES;

    public static int fieldSlices() {
        return fieldSlices;
    }

    public static void setFieldSlices(int slices) {
        fieldSlices = slices >= 4 ? 4 : slices >= 2 ? 2 : 1;
    }

    /** PERF-TEMP (stabilisation perf attribution, remove after): see PaPerfArm in the volume shader. */
    private static volatile int perfArm = 0;

    public static int perfArm() {
        return perfArm;
    }

    public static void setPerfArm(int arm) {
        perfArm = Math.max(0, Math.min(15, arm));
    }

    /** P1 (stabilisation perf): 1 = the occupancy-bit early reject in the per-step puff segment test; 0 = legacy. */
    public static final int PRODUCTION_PUFF_OCCUPANCY = 1; // P1: image-identical on clouds (p4), -0.1..0.25 ms
    private static volatile int puffOccupancy = PRODUCTION_PUFF_OCCUPANCY;

    public static int puffOccupancy() {
        return puffOccupancy;
    }

    public static void setPuffOccupancy(int mode) {
        puffOccupancy = Math.max(0, Math.min(1, mode));
    }

    /**
     * S11 (stabilisation, Codex CR-03): seeded storm structure, a bit mask - 1 turrets, 2 base rim, 4 flanking
     * line, 8 billow displacement (15 all); 0 = legacy T241 body.
     */
    public static final int PRODUCTION_STORM_STRUCTURE = 15; // E19: user accepted S11 (27 Oct); v5 clearance
    private static volatile int stormStructure = PRODUCTION_STORM_STRUCTURE;

    public static int stormStructure() {
        return stormStructure;
    }

    public static void setStormStructure(int mode) {
        stormStructure = Math.max(0, Math.min(15, mode));
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
        stormGeometryMode = PRODUCTION_STORM_GEOMETRY; // T240
        bodyMacroMode = PRODUCTION_BODY_MACRO; // T240
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

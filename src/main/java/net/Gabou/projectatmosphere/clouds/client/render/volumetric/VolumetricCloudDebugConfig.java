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
    private static volatile boolean depthCompositeEnabled = true;
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

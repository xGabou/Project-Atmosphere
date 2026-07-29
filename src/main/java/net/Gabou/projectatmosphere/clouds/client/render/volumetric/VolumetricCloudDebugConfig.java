package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * Client-only comparison switches for the weather-map volumetric renderer.
 * Defaults preserve the normal renderer. Commands may flip these at runtime
 * for A/B tests; no simulation, tuning, or generation state reads them.
 */
public final class VolumetricCloudDebugConfig {
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
    private static volatile VolumetricCloudRaymarchDebugView raymarchDebugView =
            VolumetricCloudRaymarchDebugView.FINAL;
    // Fixed empty-map base/top sentinels bleed into cloud fringes through the
    // weather map's linear filter. Keep the legacy behaviour as an A/B switch,
    // but use real fringe heights in the production path.
    private static volatile boolean sentinelHeightsEnabled;
    private static volatile boolean fullResolutionEnabled;
    private static volatile float weatherCoverageScale = 1.0F;
    private static volatile int coveragePretestSamples = 6;
    private static volatile float coveragePretestThreshold = 0.004F;
    private static volatile int coveragePretestDilation;

    private VolumetricCloudDebugConfig() {
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
        weatherCoverageScale = 1.0F;
        coveragePretestSamples = 6;
        coveragePretestThreshold = 0.004F;
        coveragePretestDilation = 0;
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
        weatherCoverageScale = 2.0F;
        coveragePretestSamples = 12;
        coveragePretestThreshold = 0.02F;
        coveragePretestDilation = 2;
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
                || weatherCoverageScale != 1.0F
                || coveragePretestSamples != 6
                || coveragePretestThreshold != 0.004F
                || coveragePretestDilation != 0) {
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
                + "\nweatherCoverageScale=" + weatherCoverageScale;
    }
}

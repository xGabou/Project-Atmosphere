package net.Gabou.projectatmosphere.clouds.client.render.field;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Client-only controls and last-frame diagnostics for synced CloudField volume
 * rendering. This bounded path is experimental, but it renders real
 * CloudFieldSnapshot objects and is not a throwaway debug visual.
 */
public final class CloudFieldVolumeRenderConfig {
    private static final long ERROR_LOG_INTERVAL_MILLIS = 10_000L;

    private static volatile boolean enabled = true;
    private static volatile CloudFieldVolumeRenderMode mode = CloudFieldVolumeRenderMode.NORMAL;
    private static volatile CloudFieldVolumeRenderFilter filter = CloudFieldVolumeRenderFilter.ALL;
    private static volatile CloudFieldCompositeDebugMode compositeDebugMode = CloudFieldCompositeDebugMode.FINAL;
    private static volatile String compositePerformanceDiagnostics = "compositeGpuMs=unavailable";
    private static volatile String pipelineTargetDiagnostics = "cloudFieldTarget=none";
    private static volatile float opacityStrength = CloudFieldVolumeTuneTarget.OPACITY.defaultValue();
    private static volatile float densityThreshold = CloudFieldVolumeTuneTarget.THRESHOLD.defaultValue();
    private static volatile float erosionStrength = CloudFieldVolumeTuneTarget.EROSION.defaultValue();
    private static volatile float noiseStrength = CloudFieldVolumeTuneTarget.NOISE.defaultValue();
    private static volatile float brightness = CloudFieldVolumeTuneTarget.BRIGHTNESS.defaultValue();
    private static volatile float undersideDarkening = CloudFieldVolumeTuneTarget.UNDERSIDE.defaultValue();
    private static volatile float maxFinalAlpha = CloudFieldVolumeTuneTarget.MAX_ALPHA.defaultValue();
    private static volatile float densityBoost = CloudFieldVolumeTuneTarget.DENSITY_BOOST.defaultValue();
    private static volatile float animSpeed = CloudFieldVolumeTuneTarget.ANIM_SPEED.defaultValue();
    private static volatile long lastErrorLogMillis;
    private static volatile CloudFieldVolumeRenderStats lastStats = CloudFieldVolumeRenderStats.idle(
            true,
            false,
            CloudFieldVolumeRenderMode.NORMAL,
            CloudFieldVolumeRenderFilter.ALL,
            "not_rendered_yet",
            0
    );

    private CloudFieldVolumeRenderConfig() {
    }

    /**
     * Enables or disables the client-side CloudField volume shader pass.
     *
     * @param shouldEnable true to draw synced CloudField snapshots
     */
    public static void setEnabled(boolean shouldEnable) {
        enabled = shouldEnable;
        try {
            AtmoCommonConfig.CLOUD_FIELD_RENDERER_ENABLED.set(shouldEnable);
            saveCommonConfigForMod(ProjectAtmosphere.MODID);
        } catch (Exception exception) {
            ProjectAtmosphere.LOGGER.warn("[CloudFieldVolume] failed to persist renderer enabled={}", shouldEnable, exception);
        }
        lastStats = CloudFieldVolumeRenderStats.idle(
                isEnabled(),
                CloudFieldVolumeShaders.isReady(),
                mode,
                filter,
                shouldEnable ? "waiting_for_render_frame" : "disabled",
                lastStats.cachedSnapshots()
        );
    }

    /**
     * Returns whether the CloudField volume shader pass should run.
     *
     * @return true when enabled by client command
     */
    public static boolean isEnabled() {
        return enabled && configuredEnabled();
    }

    /**
     * Returns whether the client cloud mixin may suppress vanilla clouds for
     * this renderer. Render exceptions flip only the volatile flag, so vanilla
     * becomes the fail-safe fallback without rewriting the user's config.
     */
    public static boolean canOwnVanillaCloudLayer() {
        return isEnabled();
    }

    /**
     * Sets the active shader debug mode.
     *
     * @param nextMode shader debug mode to use
     */
    public static void setMode(CloudFieldVolumeRenderMode nextMode) {
        mode = nextMode == null ? CloudFieldVolumeRenderMode.NORMAL : nextMode;
        lastStats = CloudFieldVolumeRenderStats.idle(
                isEnabled(),
                CloudFieldVolumeShaders.isReady(),
                mode,
                filter,
                "mode_changed_waiting_for_render_frame",
                lastStats.cachedSnapshots()
        );
    }

    /**
     * Returns the current shader debug mode.
     *
     * @return current mode
     */
    public static CloudFieldVolumeRenderMode mode() {
        return mode;
    }

    /**
     * Sets the active client-side CloudField render filter.
     *
     * @param nextFilter filter to apply before drawing fields
     */
    public static void setFilter(CloudFieldVolumeRenderFilter nextFilter) {
        filter = nextFilter == null ? CloudFieldVolumeRenderFilter.ALL : nextFilter;
        lastStats = CloudFieldVolumeRenderStats.idle(
                isEnabled(),
                CloudFieldVolumeShaders.isReady(),
                mode,
                filter,
                "filter_changed_waiting_for_render_frame",
                lastStats.cachedSnapshots()
        );
    }

    /**
     * Returns the active client-side render filter.
     *
     * @return current render filter
     */
    public static CloudFieldVolumeRenderFilter filter() {
        return filter;
    }

    public static void setCompositeDebugMode(CloudFieldCompositeDebugMode nextMode) {
        compositeDebugMode = nextMode == null ? CloudFieldCompositeDebugMode.FINAL : nextMode;
    }

    public static CloudFieldCompositeDebugMode compositeDebugMode() {
        return compositeDebugMode;
    }

    public static void recordPipelineDiagnostics(String targetDiagnostics, String compositePerformance) {
        pipelineTargetDiagnostics = targetDiagnostics == null || targetDiagnostics.isBlank()
                ? "cloudFieldTarget=none"
                : targetDiagnostics;
        compositePerformanceDiagnostics = compositePerformance == null || compositePerformance.isBlank()
                ? "compositeGpuMs=unavailable"
                : compositePerformance;
    }

    /**
     * Sets the active CloudField renderer quality profile.
     *
     * @param nextQuality quality profile to use for subsequent frames
     */
    public static void setQuality(AtmoCommonConfig.CloudRaymarchQuality nextQuality) {
        AtmoCommonConfig.CloudRaymarchQuality quality = nextQuality == null
                ? AtmoCommonConfig.CloudRaymarchQuality.MEDIUM
                : nextQuality;
        try {
            AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(quality);
            saveCommonConfigForMod(ProjectAtmosphere.MODID);
        } catch (Exception exception) {
            ProjectAtmosphere.LOGGER.warn("[CloudFieldVolume] failed to persist renderer quality={}", quality.name(), exception);
        }
        lastStats = CloudFieldVolumeRenderStats.idle(
                isEnabled(),
                CloudFieldVolumeShaders.isReady(),
                mode,
                filter,
                "quality_changed_waiting_for_render_frame",
                lastStats.cachedSnapshots()
        );
    }

    /**
     * Returns the current CloudField renderer quality profile.
     *
     * @return active quality profile
     */
    public static AtmoCommonConfig.CloudRaymarchQuality quality() {
        return AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.get();
    }

    /**
     * Returns the raymarch step count requested by the active quality profile.
     *
     * @return shader raymarch steps
     */
    public static int raymarchSteps() {
        return qualityProfile().raymarchSteps();
    }

    /**
     * Returns the per-frame field cap requested by the active quality profile.
     *
     * @return maximum rendered fields for one frame
     */
    public static int maxRenderedFields() {
        return qualityProfile().maxCloudFields();
    }

    /**
     * Returns the resolution scale for the active static quality preset.
     */
    public static float resolutionScale() {
        return qualityProfile().resolutionScale();
    }

    /**
     * Returns the number of procedural FBM octaves the shader should use.
     */
    public static int detailOctaves() {
        return qualityProfile().detailOctaves();
    }

    public static int cloudletBudget() {
        return qualityProfile().cloudletBudget();
    }

    public static CloudFieldQualityProfile qualityProfile() {
        return CloudFieldQualityProfile.forQuality(quality());
    }

    /**
     * Updates one runtime shader tuning value.
     *
     * @param target tuning value to update
     * @param value requested value, clamped to the target range
     */
    public static void setTuning(CloudFieldVolumeTuneTarget target, float value) {
        CloudFieldVolumeTuneTarget safeTarget = target == null ? CloudFieldVolumeTuneTarget.OPACITY : target;
        float clamped = safeTarget.clamp(value);
        switch (safeTarget) {
            case OPACITY -> opacityStrength = clamped;
            case THRESHOLD -> densityThreshold = clamped;
            case EROSION -> erosionStrength = clamped;
            case NOISE -> noiseStrength = clamped;
            case BRIGHTNESS -> brightness = clamped;
            case UNDERSIDE -> undersideDarkening = clamped;
            case MAX_ALPHA -> maxFinalAlpha = clamped;
            case DENSITY_BOOST -> densityBoost = clamped;
            case ANIM_SPEED -> animSpeed = clamped;
        }
        lastStats = CloudFieldVolumeRenderStats.idle(
                isEnabled(),
                CloudFieldVolumeShaders.isReady(),
                mode,
                filter,
                "tuning_changed_waiting_for_render_frame",
                lastStats.cachedSnapshots()
        );
    }

    /**
     * Applies a named client-only tuning preset to shader uniforms.
     *
     * @param preset preset to apply
     */
    public static void applyPreset(CloudFieldVolumeTunePreset preset) {
        CloudFieldVolumeTunePreset safePreset = preset == null ? CloudFieldVolumeTunePreset.SOFT : preset;
        opacityStrength = CloudFieldVolumeTuneTarget.OPACITY.clamp(safePreset.opacity());
        densityThreshold = CloudFieldVolumeTuneTarget.THRESHOLD.clamp(safePreset.threshold());
        erosionStrength = CloudFieldVolumeTuneTarget.EROSION.clamp(safePreset.erosion());
        noiseStrength = CloudFieldVolumeTuneTarget.NOISE.clamp(safePreset.noise());
        brightness = CloudFieldVolumeTuneTarget.BRIGHTNESS.clamp(safePreset.brightness());
        undersideDarkening = CloudFieldVolumeTuneTarget.UNDERSIDE.clamp(safePreset.underside());
        maxFinalAlpha = CloudFieldVolumeTuneTarget.MAX_ALPHA.clamp(safePreset.maxAlpha());
        densityBoost = CloudFieldVolumeTuneTarget.DENSITY_BOOST.clamp(safePreset.densityBoost());
        animSpeed = CloudFieldVolumeTuneTarget.ANIM_SPEED.clamp(safePreset.animSpeed());
        lastStats = CloudFieldVolumeRenderStats.idle(
                isEnabled(),
                CloudFieldVolumeShaders.isReady(),
                mode,
                filter,
                "tuning_preset_" + safePreset.serializedName() + "_waiting_for_render_frame",
                lastStats.cachedSnapshots()
        );
    }

    /**
     * Restores all runtime shader tuning values to renderer defaults.
     */
    public static void resetTuning() {
        for (CloudFieldVolumeTuneTarget target : CloudFieldVolumeTuneTarget.values()) {
            setTuning(target, target.defaultValue());
        }
        lastStats = CloudFieldVolumeRenderStats.idle(
                isEnabled(),
                CloudFieldVolumeShaders.isReady(),
                mode,
                filter,
                "tuning_reset_waiting_for_render_frame",
                lastStats.cachedSnapshots()
        );
    }

    /**
     * Reads one current runtime shader tuning value.
     *
     * @param target tuning value to read
     * @return current value for the requested target
     */
    public static float tuningValue(CloudFieldVolumeTuneTarget target) {
        CloudFieldVolumeTuneTarget safeTarget = target == null ? CloudFieldVolumeTuneTarget.OPACITY : target;
        return switch (safeTarget) {
            case OPACITY -> opacityStrength;
            case THRESHOLD -> densityThreshold;
            case EROSION -> erosionStrength;
            case NOISE -> noiseStrength;
            case BRIGHTNESS -> brightness;
            case UNDERSIDE -> undersideDarkening;
            case MAX_ALPHA -> maxFinalAlpha;
            case DENSITY_BOOST -> densityBoost;
            case ANIM_SPEED -> animSpeed;
        };
    }

    /**
     * Returns the per-sample exponential opacity strength.
     *
     * @return opacity strength uniform value
     */
    public static float opacityStrength() {
        return opacityStrength;
    }

    /**
     * Returns the density threshold below which normal mode discards samples.
     *
     * @return density threshold uniform value
     */
    public static float densityThreshold() {
        return densityThreshold;
    }

    /**
     * Returns the edge erosion multiplier.
     *
     * @return erosion strength uniform value
     */
    public static float erosionStrength() {
        return erosionStrength;
    }

    /**
     * Returns the procedural noise multiplier.
     *
     * @return noise strength uniform value
     */
    public static float noiseStrength() {
        return noiseStrength;
    }

    /**
     * Returns the neutral cloud brightness multiplier.
     *
     * @return brightness uniform value
     */
    public static float brightness() {
        return brightness;
    }

    /**
     * Returns the underside darkening amount.
     *
     * @return underside darkening uniform value
     */
    public static float undersideDarkening() {
        return undersideDarkening;
    }

    /**
     * Returns the maximum final alpha clamp.
     *
     * @return max final alpha uniform value
     */
    public static float maxFinalAlpha() {
        return maxFinalAlpha;
    }

    /**
     * Returns the normal-mode density multiplier applied before alpha.
     *
     * @return density boost uniform value
     */
    public static float densityBoost() {
        return densityBoost;
    }

    /**
     * Returns the very slow animated detail speed. Main shape noise is stable.
     *
     * @return animation speed uniform value
     */
    public static float animSpeed() {
        return animSpeed;
    }

    /**
     * Stores the latest render diagnostics for command inspection.
     *
     * @param stats last-frame stats
     */
    public static void recordStats(CloudFieldVolumeRenderStats stats) {
        lastStats = stats == null
                ? CloudFieldVolumeRenderStats.idle(isEnabled(), CloudFieldVolumeShaders.isReady(), mode, filter, "missing_stats", 0)
                : stats;
    }

    /**
     * Records a caught CloudField render exception, disables the pass, and
     * rate-limits logging so one bad frame cannot corrupt subsequent ticks.
     *
     * @param throwable render exception caught by the hook
     * @param cachedSnapshots client snapshot cache size at failure time
     */
    public static void recordRenderException(Throwable throwable, int cachedSnapshots) {
        enabled = false;
        String summary = summarizeThrowable(throwable);
        lastStats = CloudFieldVolumeRenderStats.renderError(
                CloudFieldVolumeShaders.isReady(),
                mode,
                filter,
                cachedSnapshots,
                summary
        );
        long now = System.currentTimeMillis();
        if (now - lastErrorLogMillis >= ERROR_LOG_INTERVAL_MILLIS) {
            lastErrorLogMillis = now;
            ProjectAtmosphere.LOGGER.error(
                    "[CloudFieldVolume] renderException pass disabled; use /pa cloud render on after fixing the cause. {}",
                    summary,
                    throwable
            );
        }
    }

    /**
     * Returns the latest render diagnostics.
     *
     * @return last stats snapshot
     */
    public static CloudFieldVolumeRenderStats lastStats() {
        return lastStats;
    }

    /**
     * Formats the compact current renderer state and last-frame diagnostics.
     *
     * @return multi-line status string
     */
    public static String status() {
        return lastStats.compactStatus()
                + "\n" + qualityStatus()
                + "\ncompositeMode=" + compositeDebugMode.serializedName()
                + "\n" + pipelineTargetDiagnostics
                + "\n" + compositePerformanceDiagnostics
                + "\nskyboxTextureIntegration=pending_no_skybox_texture_resource_found"
                + "\ncloudFieldRendererDefault=enabled_without_simple_clouds"
                + "\n" + tuningStatus();
    }

    /**
     * Formats verbose renderer diagnostics for deeper client debugging.
     *
     * @return multi-line verbose status string
     */
    public static String verboseStatus() {
        return lastStats.verboseStatus()
                + "\ncurrentEnabled=" + isEnabled()
                + "\nvolatileRendererEnabled=" + enabled
                + "\nconfiguredRendererEnabled=" + configuredEnabled()
                + "\ncurrentMode=" + mode.serializedName() + " (" + mode.shaderId() + ")"
                + "\ncurrentFilter=" + filter.serializedName()
                + "\ncurrentCompositeMode=" + compositeDebugMode.serializedName()
                + "\ncurrentQuality=" + serializedQualityName(quality())
                + "\n" + qualityStatus()
                + "\n" + pipelineTargetDiagnostics
                + "\n" + compositePerformanceDiagnostics
                + "\ndownscaleCompositeStatus=dedicated_paired_color_depth_upsample"
                + "\nskyboxTextureIntegration=pending_no_skybox_texture_resource_found"
                + "\ncloudFieldRendererDefault=enabled_without_simple_clouds"
                + "\n" + tuningStatus();
    }

    /**
     * Formats the current quality profile and effective render target size.
     *
     * @return single-line quality status
     */
    public static String qualityStatus() {
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        AtmoCommonConfig.CloudRaymarchQuality quality = quality();
        CloudFieldQualityProfile profile = CloudFieldQualityProfile.forQuality(quality);
        int mainWidth = mainTarget == null ? 0 : mainTarget.width;
        int mainHeight = mainTarget == null ? 0 : mainTarget.height;
        float scale = profile.resolutionScale();
        int effectiveWidth = Math.max(1, (int) Math.ceil(mainWidth * scale));
        int effectiveHeight = Math.max(1, (int) Math.ceil(mainHeight * scale));
        return "quality=" + serializedQualityName(quality)
                + " steps=" + profile.raymarchSteps()
                + " downscaleFactor=" + CloudFieldVolumeRenderStats.format(scale)
                + " effectiveTarget=" + effectiveWidth + "x" + effectiveHeight
                + " maxFields=" + profile.maxCloudFields()
                + " detailOctaves=" + profile.detailOctaves()
                + " cloudletBudget=" + profile.cloudletBudget()
                + " staticPreset=true"
                + " downscaleApplied=" + (scale < 0.999F)
                + " normalDepthStrategy=" + (scale < 0.999F
                        ? "paired_depth_aware_upsample"
                        : "direct_framebuffer_depth_test");
    }

    /**
     * Formats the current runtime shader tuning values.
     *
     * @return single-line tuning status
     */
    public static String tuningStatus() {
        return "tune.opacity=" + CloudFieldVolumeRenderStats.format(opacityStrength)
                + " tune.threshold=" + CloudFieldVolumeRenderStats.format(densityThreshold)
                + " tune.maxFinalAlpha=" + CloudFieldVolumeRenderStats.format(maxFinalAlpha)
                + " tune.noise=" + CloudFieldVolumeRenderStats.format(noiseStrength)
                + " tune.erosion=" + CloudFieldVolumeRenderStats.format(erosionStrength)
                + " tune.brightness=" + CloudFieldVolumeRenderStats.format(brightness)
                + " tune.underside=" + CloudFieldVolumeRenderStats.format(undersideDarkening)
                + " tune.densityboost=" + CloudFieldVolumeRenderStats.format(densityBoost)
                + " tune.animspeed=" + CloudFieldVolumeRenderStats.format(animSpeed);
    }

    private static String summarizeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        String type = throwable.getClass().getSimpleName();
        if (message == null || message.isBlank()) {
            return type;
        }
        String sanitized = message.replace('\n', ' ').replace('\r', ' ');
        return type + ": " + sanitized;
    }

    private static boolean configuredEnabled() {
        try {
            return AtmoCommonConfig.CLOUD_FIELD_RENDERER_ENABLED.get();
        } catch (Exception exception) {
            return true;
        }
    }

    public static String serializedQualityName(AtmoCommonConfig.CloudRaymarchQuality quality) {
        AtmoCommonConfig.CloudRaymarchQuality safeQuality = quality == null
                ? AtmoCommonConfig.CloudRaymarchQuality.MEDIUM
                : quality;
        return safeQuality.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static void saveCommonConfigForMod(String modId) {
        var set = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON);
        if (set == null) {
            return;
        }
        for (ModConfig config : set) {
            if (config.getModId().equals(modId)) {
                config.save();
                return;
            }
        }
    }
}

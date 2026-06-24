package net.Gabou.projectatmosphere.clouds.client.render.field;

import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;

/**
 * Client-only controls and last-frame diagnostics for the bounded CloudField
 * volume prototype renderer. This experimental path is command-enabled and is
 * not the final global raymarcher, cloud shadow path, or Atmospheric Shaders
 * integration.
 */
public final class CloudFieldVolumeRenderConfig {
    private static volatile boolean enabled;
    private static volatile CloudFieldVolumeRenderMode mode = CloudFieldVolumeRenderMode.NORMAL;
    private static volatile CloudFieldVolumeRenderFilter filter = CloudFieldVolumeRenderFilter.ALL;
    private static volatile float opacityStrength = CloudFieldVolumeTuneTarget.OPACITY.defaultValue();
    private static volatile float densityThreshold = CloudFieldVolumeTuneTarget.THRESHOLD.defaultValue();
    private static volatile float erosionStrength = CloudFieldVolumeTuneTarget.EROSION.defaultValue();
    private static volatile float noiseStrength = CloudFieldVolumeTuneTarget.NOISE.defaultValue();
    private static volatile float brightness = CloudFieldVolumeTuneTarget.BRIGHTNESS.defaultValue();
    private static volatile float undersideDarkening = CloudFieldVolumeTuneTarget.UNDERSIDE.defaultValue();
    private static volatile float maxFinalAlpha = CloudFieldVolumeTuneTarget.MAX_ALPHA.defaultValue();
    private static volatile float densityBoost = CloudFieldVolumeTuneTarget.DENSITY_BOOST.defaultValue();
    private static volatile float animSpeed = CloudFieldVolumeTuneTarget.ANIM_SPEED.defaultValue();
    private static volatile CloudFieldVolumeRenderStats lastStats = CloudFieldVolumeRenderStats.idle(
            false,
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
        lastStats = CloudFieldVolumeRenderStats.idle(
                enabled,
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
        return enabled;
    }

    /**
     * Sets the active shader debug mode.
     *
     * @param nextMode shader debug mode to use
     */
    public static void setMode(CloudFieldVolumeRenderMode nextMode) {
        mode = nextMode == null ? CloudFieldVolumeRenderMode.NORMAL : nextMode;
        lastStats = CloudFieldVolumeRenderStats.idle(
                enabled,
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
                enabled,
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
                enabled,
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
                enabled,
                CloudFieldVolumeShaders.isReady(),
                mode,
                filter,
                "tuning_preset_" + safePreset.serializedName() + "_waiting_for_render_frame",
                lastStats.cachedSnapshots()
        );
    }

    /**
     * Restores all runtime shader tuning values to prototype defaults.
     */
    public static void resetTuning() {
        for (CloudFieldVolumeTuneTarget target : CloudFieldVolumeTuneTarget.values()) {
            setTuning(target, target.defaultValue());
        }
        lastStats = CloudFieldVolumeRenderStats.idle(
                enabled,
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
                ? CloudFieldVolumeRenderStats.idle(enabled, CloudFieldVolumeShaders.isReady(), mode, filter, "missing_stats", 0)
                : stats;
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
                + "\nexperimentalVolumeRendererDefault=disabled_until_command_enabled"
                + "\n" + tuningStatus();
    }

    /**
     * Formats verbose renderer diagnostics for deeper client debugging.
     *
     * @return multi-line verbose status string
     */
    public static String verboseStatus() {
        return lastStats.verboseStatus()
                + "\ncurrentEnabled=" + enabled
                + "\ncurrentMode=" + mode.serializedName() + " (" + mode.shaderId() + ")"
                + "\ncurrentFilter=" + filter.serializedName()
                + "\nexperimentalVolumeRendererDefault=disabled_until_command_enabled"
                + "\n" + tuningStatus();
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
}

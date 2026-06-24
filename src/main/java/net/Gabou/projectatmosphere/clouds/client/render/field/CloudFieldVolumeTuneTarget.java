package net.Gabou.projectatmosphere.clouds.client.render.field;

import org.jetbrains.annotations.NotNull;

/**
 * Runtime shader tuning values exposed by the CloudField volume debug command.
 */
public enum CloudFieldVolumeTuneTarget {
    OPACITY("opacity", 0.0F, 1.5F, 0.420F),
    THRESHOLD("threshold", 0.0F, 0.05F, 0.0012F),
    EROSION("erosion", 0.0F, 2.0F, 1.0F),
    NOISE("noise", 0.0F, 2.0F, 1.0F),
    BRIGHTNESS("brightness", 0.2F, 2.5F, 1.08F),
    UNDERSIDE("underside", 0.0F, 0.9F, 0.34F),
    MAX_ALPHA("maxalpha", 0.05F, 1.0F, 0.95F),
    DENSITY_BOOST("densityboost", 0.1F, 5.0F, 2.0F),
    ANIM_SPEED("animspeed", 0.0F, 0.2F, 0.015F);

    private final String serializedName;
    private final float min;
    private final float max;
    private final float defaultValue;

    CloudFieldVolumeTuneTarget(@NotNull String serializedName, float min, float max, float defaultValue) {
        this.serializedName = serializedName;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
    }

    public @NotNull String serializedName() {
        return serializedName;
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    public float defaultValue() {
        return defaultValue;
    }

    public float clamp(float value) {
        if (!Float.isFinite(value)) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }
}

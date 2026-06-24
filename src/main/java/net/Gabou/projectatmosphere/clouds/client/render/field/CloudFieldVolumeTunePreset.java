package net.Gabou.projectatmosphere.clouds.client.render.field;

import org.jetbrains.annotations.NotNull;

/**
 * Client-only shader tuning presets for the bounded CloudField volume prototype.
 * These values affect uniforms only and never modify backend CloudField state.
 */
public enum CloudFieldVolumeTunePreset {
    SOFT("soft", 0.420F, 0.0012F, 1.0F, 1.0F, 1.08F, 0.34F, 0.95F, 2.0F, 0.015F),
    DENSE("dense", 0.620F, 0.0008F, 0.85F, 0.85F, 1.06F, 0.46F, 0.98F, 2.6F, 0.012F),
    WISPY("wispy", 0.260F, 0.0035F, 1.45F, 1.35F, 1.12F, 0.24F, 0.78F, 1.35F, 0.018F),
    DEBUG("debug", 0.760F, 0.0005F, 0.35F, 0.55F, 1.30F, 0.20F, 0.99F, 2.8F, 0.0F);

    private final String serializedName;
    private final float opacity;
    private final float threshold;
    private final float erosion;
    private final float noise;
    private final float brightness;
    private final float underside;
    private final float maxAlpha;
    private final float densityBoost;
    private final float animSpeed;

    CloudFieldVolumeTunePreset(
            @NotNull String serializedName,
            float opacity,
            float threshold,
            float erosion,
            float noise,
            float brightness,
            float underside,
            float maxAlpha,
            float densityBoost,
            float animSpeed
    ) {
        this.serializedName = serializedName;
        this.opacity = opacity;
        this.threshold = threshold;
        this.erosion = erosion;
        this.noise = noise;
        this.brightness = brightness;
        this.underside = underside;
        this.maxAlpha = maxAlpha;
        this.densityBoost = densityBoost;
        this.animSpeed = animSpeed;
    }

    public @NotNull String serializedName() {
        return serializedName;
    }

    public float opacity() {
        return opacity;
    }

    public float threshold() {
        return threshold;
    }

    public float erosion() {
        return erosion;
    }

    public float noise() {
        return noise;
    }

    public float brightness() {
        return brightness;
    }

    public float underside() {
        return underside;
    }

    public float maxAlpha() {
        return maxAlpha;
    }

    public float densityBoost() {
        return densityBoost;
    }

    public float animSpeed() {
        return animSpeed;
    }
}

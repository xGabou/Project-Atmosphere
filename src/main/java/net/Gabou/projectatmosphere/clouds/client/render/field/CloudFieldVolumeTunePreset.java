package net.Gabou.projectatmosphere.clouds.client.render.field;

import org.jetbrains.annotations.NotNull;

/**
 * Client-only shader tuning presets for the bounded CloudField volume renderer.
 * These values affect uniforms only and never modify backend CloudField state.
 */
public enum CloudFieldVolumeTunePreset {
    SOFT("soft", 0.340F, 0.0016F, 1.20F, 1.0F, 1.0F, 0.52F, 0.90F, 1.55F, 0.012F),
    DENSE("dense", 0.480F, 0.0012F, 1.05F, 0.95F, 1.02F, 0.56F, 0.94F, 2.10F, 0.010F),
    WISPY("wispy", 0.240F, 0.0035F, 1.50F, 1.35F, 1.10F, 0.38F, 0.74F, 1.15F, 0.014F),
    DEBUG("debug", 0.620F, 0.0007F, 0.55F, 0.65F, 1.18F, 0.42F, 0.96F, 2.30F, 0.0F);

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

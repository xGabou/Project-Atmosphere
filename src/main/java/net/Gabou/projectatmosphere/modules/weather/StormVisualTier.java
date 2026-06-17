package net.Gabou.projectatmosphere.modules.weather;

import net.minecraft.util.Mth;

public enum StormVisualTier {
    CLEAR(0.0F, 0.0F),
    CLOUDY(0.18F, 0.08F),
    RAIN_CORE(0.38F, 0.22F),
    THUNDER_CORE(0.62F, 0.42F),
    SEVERE_CORE(0.82F, 0.65F),
    CYCLONE_CORE(1.0F, 0.78F);

    private final float darkness;
    private final float shadowBias;

    StormVisualTier(float darkness, float shadowBias) {
        this.darkness = darkness;
        this.shadowBias = shadowBias;
    }

    public float getDarkness() {
        return darkness;
    }

    public float getShadowBias() {
        return shadowBias;
    }

    public static StormVisualTier fromSeverity(float severity) {
        float value = Mth.clamp(severity, 0.0F, 1.0F);
        if (value >= 0.86F) {
            return CYCLONE_CORE;
        }
        if (value >= 0.68F) {
            return SEVERE_CORE;
        }
        if (value >= 0.48F) {
            return THUNDER_CORE;
        }
        if (value >= 0.25F) {
            return RAIN_CORE;
        }
        if (value > 0.02F) {
            return CLOUDY;
        }
        return CLEAR;
    }
}

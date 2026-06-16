package net.Gabou.projectatmosphere.seasons;

import net.minecraft.util.Mth;

/**
 * Shared seasonal climate curve used by season providers and live drift.
 */
public final class SeasonClimateProfile {
    private SeasonClimateProfile() {
    }

    public static float temperatureOffsetC(SeasonStage stage, float progress) {
        if (stage == null || stage == SeasonStage.NEUTRAL) {
            return 0f;
        }
        float strength = seasonalStrength(progress);
        return switch (stage) {
            case SPRING -> 2.0f * strength;
            case SUMMER -> 6.0f * strength;
            case AUTUMN -> -2.0f * strength;
            case WINTER -> -8.0f * strength;
            case NEUTRAL -> 0f;
        };
    }

    public static float seasonalStrength(float progress) {
        float clamped = Mth.clamp(progress, 0f, 1f);
        return 0.75f + 0.25f * Mth.sin(clamped * (float) Math.PI);
    }
}

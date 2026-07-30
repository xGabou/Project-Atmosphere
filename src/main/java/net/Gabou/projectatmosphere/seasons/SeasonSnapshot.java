package net.Gabou.projectatmosphere.seasons;

import net.minecraft.resources.ResourceLocation;

/**
 * Lightweight season snapshot that callers can use without knowing the backing mod.
 */
public record SeasonSnapshot(ResourceLocation providerId,
                             SeasonStage stage,
                             float progress,         // 0..1 within the current year/cycle
                             float temperatureOffset, // degrees Celsius offset to apply
                             SeasonMoistureStage moistureStage,
                             float moistureProgress
) {
    public SeasonSnapshot {
        providerId = providerId == null
                ? ResourceLocation.fromNamespaceAndPath("projectatmosphere", "neutral")
                : providerId;
        stage = stage == null ? SeasonStage.NEUTRAL : stage;
        progress = clamp01(progress);
        temperatureOffset = Float.isFinite(temperatureOffset) ? temperatureOffset : 0.0f;
        moistureStage = moistureStage == null ? SeasonMoistureStage.NEUTRAL : moistureStage;
        moistureProgress = clamp01(moistureProgress);
    }

    public SeasonSnapshot(ResourceLocation providerId,
                          SeasonStage stage,
                          float progress,
                          float temperatureOffset) {
        this(providerId, stage, progress, temperatureOffset, SeasonMoistureStage.NEUTRAL, 0.0f);
    }

    public static SeasonSnapshot neutral() {
        return new SeasonSnapshot(ResourceLocation.fromNamespaceAndPath("projectatmosphere", "neutral"),
                SeasonStage.NEUTRAL, 0.0f, 0.0f, SeasonMoistureStage.NEUTRAL, 0.0f);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}

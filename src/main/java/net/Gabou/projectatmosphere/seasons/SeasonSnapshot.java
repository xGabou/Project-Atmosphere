package net.Gabou.projectatmosphere.seasons;

import net.minecraft.resources.ResourceLocation;

/**
 * Lightweight season snapshot that callers can use without knowing the backing mod.
 */
public record SeasonSnapshot(ResourceLocation providerId,
                             SeasonStage stage,
                             float progress,         // 0..1 within the current year/cycle
                             float temperatureOffset // degrees Celsius offset to apply
) {
    public static SeasonSnapshot neutral() {
        return new SeasonSnapshot(ResourceLocation.fromNamespaceAndPath("projectatmosphere", "neutral"),
                SeasonStage.NEUTRAL, 0.0f, 0.0f);
    }
}

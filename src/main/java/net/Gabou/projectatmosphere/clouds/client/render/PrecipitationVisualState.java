package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.modules.weather.PrecipitationTier;
import net.Gabou.projectatmosphere.modules.weather.SnowTier;
import net.minecraft.core.BlockPos;

public record PrecipitationVisualState(
        PrecipitationTier rainTier,
        SnowTier snowTier,
        float rainIntensity,
        float thunderIntensity,
        float windSlantX,
        float windSlantZ,
        float fogBoost,
        float splashIntensity,
        BlockPos samplePos
) {
    public static final PrecipitationVisualState NONE = new PrecipitationVisualState(
            PrecipitationTier.NONE,
            SnowTier.NONE,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            BlockPos.ZERO
    );

    public boolean hasCustomVisualWeather() {
        return rainTier != PrecipitationTier.NONE || snowTier != SnowTier.NONE;
    }

    public boolean isHeavyWeather() {
        return rainTier == PrecipitationTier.HEAVY_RAIN || snowTier == SnowTier.BLIZZARD;
    }
}

package net.Gabou.projectatmosphere.compat.auroras;

import net.Gabou.projectatmosphere.compat.temperature.ClientTemperatureResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

/**
 * Computes aurora brightness multipliers using Serene Seasons and biome temperature data.
 */
@OnlyIn(Dist.CLIENT)
public final class AuroraSeasonHelper {

    private AuroraSeasonHelper() {
    }

    public static float computeSeasonalFactor(Level level) {
        if (level == null) {
            return 1.0f;
        }
        try {
            Season.SubSeason subSeason = SeasonHelper.getSeasonState(level).getSubSeason();
            Season season = subSeason.getSeason();
            return switch (season) {
                case WINTER -> 1.4f;
                case AUTUMN -> 1.1f;
                case SPRING -> 0.85f;
                case SUMMER -> 0.55f;
            };
        } catch (Exception ignored) {
            // Serene Seasons might not be ready yet on the client – fall back to neutral factor.
            return 1.0f;
        }
    }

    public static float computeTemperatureFactor(Level level, BlockPos pos) {
        if (level == null) {
            return 1.0f;
        }
        Holder<Biome> biomeHolder = level.getBiome(pos);
        Biome biome = biomeHolder.value();
        if (biome.coldEnoughToSnow(pos)) {
            return 1.35f;
        }
        float tempCelsius = ClientTemperatureResolver.getCelsius(level, pos);
        // Map the PA temperature range (roughly -20°C → 35°C) to a brightness boost.
        // Colder environments yield brighter auroras while warmer biomes dim them.
        float normalized = (15.0f - tempCelsius) / 20.0f;
        float scaled = 0.6f + Mth.clamp(normalized, 0.0f, 1.0f) * 0.75f;
        return Mth.clamp(scaled, 0.45f, 1.35f);
    }

    public static float combinedBoost(Level level, BlockPos pos) {
        float seasonal = computeSeasonalFactor(level);
        float thermal = computeTemperatureFactor(level, pos);
        return Mth.clamp(seasonal * thermal, 0.45f, 1.5f);
    }
}

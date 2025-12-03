package net.Gabou.projectatmosphere.modules.region;

import java.util.List;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

/**
 * Adapter that sources biome slices from the existing ForecastGenerator biome forecasts.
 */
public final class LegacyBiomeForecastGenerator implements BiomeForecastGenerator {
    @Override
    public BiomeForecastSnapshot generateSlice(List<BiomeInstanceKey> biomes, int sliceIndex) {
        if (biomes == null || biomes.isEmpty()) {
            return null;
        }
        BiomeInstanceKey key = biomes.get(sliceIndex % biomes.size());
        BiomeForecast forecast = ForecastGenerator.getForecastMap().get(key);
        if (forecast == null) {
            forecast = ForecastGenerator.getClosestValidForecast(key, ForecastType.TEMPERATURE);
        }
        if (forecast == null) {
            return null;
        }
        return BiomeForecastSnapshot.from(forecast);
    }

    @Override
    public float factorForSlice(List<BiomeInstanceKey> biomes, int sliceIndex) {
        if (biomes == null || biomes.isEmpty()) {
            return 0f;
        }
        // Simple even split; replace with weighted area/variance if available.
        return 1f / 8f;
    }
}

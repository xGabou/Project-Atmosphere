package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Base for any “weekly forecast” generator (TemperatureForecast, StormForecast, etc).
 * @param <T>  the per‐biome forecast type (e.g. float[][] or double[])
 */
public abstract class BaseForecastGenerator<T> {

    /**
     * Find all biomes & their sample positions in a square around center.
     * You can reuse your existing findBiomes logic here.
     */
    protected abstract Map<ResourceLocation, BlockPos> findBiomeSamples(
            Level world, BlockPos center, int radius);

    /** Produce a forecast for one biome at its samplePos. */
    protected abstract T computeForecast(
            Level world, BlockPos samplePos, ResourceLocation biome);

    /**
     * Main entry: scan area, generate (and optionally cache) a forecast per‐biome.
     */
    public Map<ResourceLocation, T> generateForecastAround(
            Level world, BlockPos center, int radius) {

        Map<ResourceLocation, T> forecasts = new HashMap<>();
        for (var e : findBiomeSamples(world, center, radius).entrySet()) {
            ResourceLocation biome = e.getKey();
            BlockPos sample = e.getValue();

            // optional hook: record sample
            saveSamplePosition(biome, sample);

            T week = computeForecast(world, sample, biome);

            // optional hook: store it
            onForecastGenerated(biome, week);

            forecasts.put(biome, week);
        }
        return forecasts;
    }

    /** For temporary/no‐cache forecasts. */
    public Map<ResourceLocation, T> generateTemporaryForecastAround(
            Level world, BlockPos center, int radius) {
        // default: same as generateForecast but without onForecastGenerated()
        Map<ResourceLocation, T> result = new HashMap<>();
        for (var e : findBiomeSamples(world, center, radius).entrySet()) {
            result.put(e.getKey(),
                    computeForecast(world, e.getValue(), e.getKey()));
        }
        return result;
    }

    /** Optional: record sample positions for debugging. */
    protected void saveSamplePosition(ResourceLocation biome, BlockPos pos) {}

    /** Optional: cache or persist the freshly‐generated forecast. */
    protected void onForecastGenerated(ResourceLocation biome, T forecast) {}
}

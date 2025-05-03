package net.Gabou.projectatmosphere.temperature.forcast;

import net.Gabou.projectatmosphere.temperature.util.DailyProfileGenerator;
import net.Gabou.projectatmosphere.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.temperature.util.TemperatureUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TemperatureForecast {

    /**
     * Scans all unique biomes in a radius (500×500 area), generates weekly forecasts per biome,
     * stores them in ForecastStorageManager, and kicks off daily profile generation.
     *
     * @return biome → [7 days][min/max]
     */
    public static Map<ResourceLocation, float[][]> generateForecastAround(Level world, BlockPos center, int radiusBlocks) {
        Set<ResourceLocation> foundBiomes = new HashSet<>();
        Map<ResourceLocation, float[][]> forecasts = new HashMap<>();

        int step = 16; // one sample per chunk
        int radius = radiusBlocks;

        // 1) Collect biomes in area
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                BlockPos samplePos = center.offset(dx, 0, dz);
                ResourceLocation biomeKey = world.getBiome(samplePos)
                        .unwrapKey().get()
                        .location();
                foundBiomes.add(biomeKey);
            }
        }

        // 2) Generate weekly forecast per biome, store in both local map & storage manager

            for (ResourceLocation biome : foundBiomes) {
                if (ForecastStorageManager.hasForecast(world, biome)) {
                    forecasts.put(biome, ForecastStorageManager.getForecast(biome));
                } else {
                    float[][] forecast = TemperatureUtils.generateWeekForecast(world, center, biome);
                    forecasts.put(biome, forecast);// Store it for future access
                }
            }



        return forecasts;
    }
}

package net.Gabou.projectatmosphere.temperature.forecast;

import net.Gabou.projectatmosphere.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.temperature.util.TemperatureGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.*;

public class TemperatureForecast {

    /**
     * Scans a 500×500 area, uses cached forecasts when present,
     * otherwise generates and saves new weekly forecasts.
     */
    public static Map<ResourceLocation, float[][]> generateForecastAround(
            Level world, BlockPos center, int radiusBlocks) {

        Set<ResourceLocation> foundBiomes = new HashSet<>();
        Map<ResourceLocation, float[][]> forecasts = new HashMap<>();
        int step = 16, radius = radiusBlocks;

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                ResourceLocation biome = world.getBiome(center.offset(dx, 0, dz))
                        .unwrapKey().get().location();
                foundBiomes.add(biome);
            }
        }

        for (ResourceLocation biome : foundBiomes) {
            if (ForecastStorageManager.hasForecast(biome)) {
                forecasts.put(biome, ForecastStorageManager.getForecast(biome));
            } else {
                float[][] week = TemperatureGenerator.generateWeekForecast(world, center, biome);
                forecasts.put(biome, week);
                ForecastStorageManager.saveForecast(biome, week);
            }
        }

        return forecasts;
    }
}

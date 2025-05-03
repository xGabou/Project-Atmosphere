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
    public static Map<ResourceLocation, float[][]> generateForecastAround(Level world, BlockPos center, int radiusBlocks) {
        // Skip if not in overworld
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return Collections.emptyMap();
        }

        Set<ResourceLocation> foundBiomes = new HashSet<>();
        Map<ResourceLocation, BlockPos> biomeSamples = new HashMap<>();
        Map<ResourceLocation, float[][]> forecasts = new HashMap<>();

        int step = 16;

        // Step 1: Find all biomes around the area
        for (int dx = -radiusBlocks; dx <= radiusBlocks; dx += step) {
            for (int dz = -radiusBlocks; dz <= radiusBlocks; dz += step) {
                BlockPos pos = center.offset(dx, 0, dz);
                ResourceLocation biomeKey = world.getBiome(pos).unwrapKey().get().location();

                if (foundBiomes.add(biomeKey)) {
                    biomeSamples.put(biomeKey, pos); // save a real sample pos for this biome
                }
            }
        }

        // Step 2: Generate forecast for each biome based on its real sample position
        for (Map.Entry<ResourceLocation, BlockPos> entry : biomeSamples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos samplePos = entry.getValue();

            if (ForecastStorageManager.hasForecast(biome)) {
                forecasts.put(biome, ForecastStorageManager.getForecast(biome));
            } else {
                float[][] week = TemperatureGenerator.generateWeekForecast(world, samplePos, biome);
                forecasts.put(biome, week);
                ForecastStorageManager.saveForecast(biome, week);
            }
        }

        return forecasts;
    }

}

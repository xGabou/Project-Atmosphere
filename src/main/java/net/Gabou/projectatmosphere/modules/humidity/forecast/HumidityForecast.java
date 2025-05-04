// src/main/java/net/Gabou/projectatmosphere/modules/humidity/forecast/HumidityForecast.java
package net.Gabou.projectatmosphere.modules.humidity.forecast;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityStorageManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class HumidityForecast {

    /**
     * Scans around center, loads cached or generates new weekly humidity forecasts.
     */
    public static Map<ResourceLocation, float[][]> generateForecastAround(
            ServerLevel world, BlockPos center, int radiusBlocks) {
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return Map.of();
        }

        // findBiomes now returns a map of biome→samplePos
        Map<ResourceLocation, BlockPos> samples =
                AtmosphereUtils.findBiomes(world, center, radiusBlocks);

        Map<ResourceLocation, float[][]> forecasts = new HashMap<>();
        for (var entry : samples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos pos = entry.getValue();


            float[][] week;
            if (HumidityStorageManager.hasForecast(biome)) {
                week = HumidityStorageManager.getForecast(biome);
            } else {
                week = HumidityGenerator.generateWeekForecast(world, pos, biome);
                HumidityStorageManager.putForecast(biome, week);
            }
            forecasts.put(biome, week);
        }
        return forecasts;
    }

    /**
     * Same as above, but generates without saving to storage.
     */
    public static Map<ResourceLocation, float[][]> generateTemporaryForecastAround(
            ServerLevel world, BlockPos center, int radiusBlocks) {
        Map<ResourceLocation, BlockPos> samples =
                AtmosphereUtils.findBiomes(world, center, radiusBlocks);

        Map<ResourceLocation, float[][]> forecasts = new HashMap<>();
        for (var entry : samples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos pos = entry.getValue();
            float[][] week = HumidityGenerator.generateWeekForecast(world, pos, biome);
            forecasts.put(biome, week);
        }
        return forecasts;
    }
}

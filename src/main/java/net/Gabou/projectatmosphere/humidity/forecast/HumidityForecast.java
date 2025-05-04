// net/Gabou/projectatmosphere/humidity/forecast/HumidityForecast.java
package net.Gabou.projectatmosphere.humidity.forecast;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.humidity.util.HumidityStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

public class HumidityForecast {
    /**
     * Scan a square of radiusBlocks around center, pull cached weekly forecasts
     * or generate+save new ones.
     */
    public static Map<ResourceLocation, float[][]> generateForecastAround(
            Level world, BlockPos center, int radiusBlocks) {
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return Collections.emptyMap();
        }

        Set<ResourceLocation> found = new HashSet<>();
        Map<ResourceLocation, BlockPos> samples = new HashMap<>();
        AtmosphereUtils.findBiomes(world, center, radiusBlocks, found, samples);

        Map<ResourceLocation, float[][]> forecasts = new HashMap<>();
        for (var e : samples.entrySet()) {
            ResourceLocation biome = e.getKey();
            BlockPos pos = e.getValue();

            HumidityStorageManager.saveSamplePosition(biome, pos);

            if (HumidityStorageManager.hasForecast(biome)) {
                forecasts.put(biome, HumidityStorageManager.getForecast(biome));
            } else {
                // TODO: replace with your humidity‐generation logic
                float[][] week = HumidityGenerator.generateWeekForecast(world, pos, biome);
                forecasts.put(biome, week);
                HumidityStorageManager.saveForecast(biome, week);
            }
        }
        return forecasts;
    }

    public static Map<ResourceLocation, float[][]> generateTemporaryForecastAround(
            ServerLevel world, BlockPos center, int radius) {
        // same as above but do not save into storage
        // ...
        return Collections.emptyMap();
    }
}

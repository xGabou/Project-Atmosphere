// src/main/java/net/Gabou/projectatmosphere/modules/humidity/forecast/HumidityForecast.java
package net.Gabou.projectatmosphere.modules.humidity.forecast;

import net.Gabou.projectatmosphere.modules.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityStorageManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityGenerator;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HumidityForecast {

    /**
     * Scans around center, loads cached or generates new weekly humidity forecasts.
     */
    public static void generateForecastAround(
            ServerLevel world, BlockPos center, int radiusBlocks) {
        // findBiomes now returns a map of biome→samplePos
        Set<BiomeInstanceKey> samples =
                AtmosphereUtils.findBiomes(world, center, radiusBlocks);
        for (var entry : samples) {
            float[][] week;
            if (HumidityStorageManager.hasForecast(entry)) {
                HumidityProfileManager.putWeeklyForecast(entry,HumidityStorageManager.getForecast(entry));
            }
            else {
                week = HumidityGenerator.generateWeekForecast(world, entry);
                HumidityStorageManager.putForecast(entry, week);
                HumidityProfileManager.putWeeklyForecast(entry, week);
            }

        }
    }

    /**
     * Same as above, but generates without saving to storage.
     */
    public static Map<BiomeInstanceKey, float[][]> generateTemporaryForecastAround(
            ServerLevel world, BlockPos center, int radiusBlocks) {
        Set<BiomeInstanceKey> samples =
                AtmosphereUtils.findBiomes(world, center, radiusBlocks);

        Map<BiomeInstanceKey, float[][]> forecasts = new HashMap<>();
        for (var entry : samples) {
            ResourceLocation biome = entry.biomeType();
            BlockPos pos = entry.samplePos();
            float[][] week = HumidityGenerator.generateWeekForecast(world,entry);
            forecasts.put(entry, week);
        }
        return forecasts;
    }
}

// net/Gabou/projectatmosphere/storm/spike/StormSpikeManager.java
package net.Gabou.projectatmosphere.modules.storm.spike;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.*;

public class StormSpikeManager {
    public static Map<ResourceLocation,double[]> generateForecastAround(
            Level world, BlockPos center, int radius) {
        Set<ResourceLocation> found = new HashSet<>();
        Map<ResourceLocation, BlockPos> samples = new HashMap<>();
        AtmosphereUtils.findBiomes(world, center, radius, found, samples);

        Map<ResourceLocation,double[]> forecasts = new HashMap<>();
        for (var e : samples.entrySet()) {
            ResourceLocation biome = e.getKey();
            BlockPos pos = e.getValue();

            StormStorageManager.saveSamplePosition(biome, pos);

            if (StormStorageManager.hasForecast(biome)) {
                forecasts.put(biome, StormStorageManager.getForecast(biome));
            } else {
                // TODO: Replace with real storm‐spike generation
                double[] week = new double[7];
                for (int i = 0; i < 7; i++) {
                    week[i] = randomStormSpike(biome, i);
                }
                forecasts.put(biome, week);
                StormStorageManager.saveForecast(biome, week);
            }
        }
        return forecasts;
    }

    /** Returns a pseudo-random ±hPa spike for this biome/day. */
    public static double randomStormSpike(ResourceLocation biome, int day) {
        // TODO: tie into your storm logic
        return (Math.random() - 0.5) * 20.0;
    }
}

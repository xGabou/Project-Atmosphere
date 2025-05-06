// net/Gabou/projectatmosphere/storm/spike/StormSpikeManager.java
package net.Gabou.projectatmosphere.modules.storm.spike;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.*;

public class StormSpikeManager {
    public static Map<BiomeInstanceKey,float[]> generateForecastAround(
            Level world, BlockPos center, int radius) {
        Set<BiomeInstanceKey> found = AtmosphereUtils.findBiomes(world, center, radius);
        Map<BiomeInstanceKey,float[]> forecasts = new HashMap<>();
        for (var e : found) {

            if (StormStorageManager.hasForecast(e)) {
                forecasts.put(e, StormStorageManager.getForecast(e));
            } else {
                // TODO: Replace with real storm‐spike generation
                float[] week = new float[7];
                for (int i = 0; i < 7; i++) {
                    week[i] = randomStormSpike(e, i);
                }
                forecasts.put(e, week);
                StormStorageManager.saveForecast(e, week);
            }
        }
        return forecasts;
    }

    /** Returns a pseudo-random ±hPa spike for this biome/day. */
    public static float randomStormSpike(BiomeInstanceKey biome, int day) {
        // TODO: tie into your storm logic
        return (float) ((Math.random() - 0.5f) * 20.0f);
    }
}

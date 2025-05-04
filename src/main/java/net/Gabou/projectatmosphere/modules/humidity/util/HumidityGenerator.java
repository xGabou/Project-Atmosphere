// src/main/java/net/Gabou/projectatmosphere/modules/humidity/util/HumidityGenerator.java
package net.Gabou.projectatmosphere.modules.humidity.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class HumidityGenerator {

    /**
     * Generates a weekly (7-day) humidity forecast for a given biome sample.
     * Returns a float[7][2] where each entry is {minHumidity%, maxHumidity%}.
     */
    public static float[][] generateWeekForecast(Level world, BlockPos samplePos, ResourceLocation biomeId) {
        Biome biome = world.getBiome(samplePos).get();
        // Downfall is 0.0–1.0; scale to 0–100%
        float base = biome.getModifiedClimateSettings().downfall() * 100f;

        float[][] week = new float[7][2];
        for (int day = 0; day < 7; day++) {
            // simple +/- variation of ±10%
            float min = clamp(base - 10f);
            float max = clamp(base + 10f);
            week[day][0] = min;
            week[day][1] = max;
        }
        return week;
    }

    private static float clamp(float v) {
        return v < (float) 0.0 ? (float) 0.0 : (Math.min(v, (float) 100.0));
    }
}

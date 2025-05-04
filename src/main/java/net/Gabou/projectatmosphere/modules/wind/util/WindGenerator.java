package net.Gabou.projectatmosphere.modules.wind.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

/**
 * Utility for generating weekly wind speed forecasts.
 * Values are in meters per second (m/s).
 */
public class WindGenerator {

    private static final Random random = new Random();

    public static float[][] generateWeeklyWindProfile(ServerLevel world, BlockPos pos, ResourceLocation biome) {
        float[][] forecast = new float[7][2];

        for (int i = 0; i < 7; i++) {
            float base = 4.0f + random.nextFloat() * 4.0f;     // Base between 4–8 m/s
            float variation = random.nextFloat() * 2.0f;        // Random addition 0–2 m/s

            float min = base;
            float max = base + variation;

            forecast[i][0] = min;
            forecast[i][1] = max;
        }

        return forecast;
    }
}

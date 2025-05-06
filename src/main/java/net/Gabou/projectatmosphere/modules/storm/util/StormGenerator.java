package net.Gabou.projectatmosphere.modules.storm.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

/**
 * Generator for 7-day storm profiles for a given biome.
 * Values can represent storm intensity, from 0 (clear) to 1 (severe storm).
 */
public class StormGenerator {

    private static final Random random = new Random();

    /**
     * Generates a 7-day storm profile for a given biome and position.
     * Values range from 0.0 (no storm) to 1.0 (intense storm).
     */
    public static float[] generateWeeklyStormProfile(ServerLevel world, BlockPos pos, ResourceLocation biome) {
        float[] week = new float[7];

        // Example logic: some randomness + pseudo-seasonal effect
        for (int i = 0; i < 7; i++) {
            // Base storm probability based on biome roughness or elevation (could be expanded)
            float base = 0.2f;

            // Random daily fluctuation
            float fluctuation = 0.2f * (random.nextFloat() - 0.5f);

            // Simulate occasional stronger storms
            if (random.nextFloat() < 0.1f) {
                fluctuation += 0.5f;
            }

            week[i] = clamp(base + fluctuation, 0.0f, 1.0f);
        }

        return week;
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}

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
    public static double[] generateWeeklyStormProfile(ServerLevel world, BlockPos pos, ResourceLocation biome) {
        double[] week = new double[7];

        // Example logic: some randomness + pseudo-seasonal effect
        for (int i = 0; i < 7; i++) {
            // Base storm probability based on biome roughness or elevation (could be expanded)
            double base = 0.2;

            // Random daily fluctuation
            double fluctuation = 0.2 * (random.nextDouble() - 0.5);

            // Simulate occasional stronger storms
            if (random.nextDouble() < 0.1) {
                fluctuation += 0.5;
            }

            week[i] = clamp(base + fluctuation, 0.0, 1.0);
        }

        return week;
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}

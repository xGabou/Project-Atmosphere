package net.Gabou.projectatmosphere.modules.storm;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

public class StormGenerator {

    private static final float DEFAULT_MIN = 0.0f;
    private static final float DEFAULT_MAX = 1.0f;

    /**
     * Returns a 7-day storm profile, each day having [min, max] chance.
     */
    public static float[][] generateWeeklyStormProfile(
            BiomeInstanceKey biome, ServerLevel level,
            float[][] temperature,
            float[][] humidity,
            float[][] pressure,
            WindVector[] wind
    ) {
        float[][] stormWeek = new float[7][2];
        BlockPos pos = biome.samplePos();

        for (int day = 0; day < 7; day++) {
            float stormScore = 0f;

            float tempAvg = (temperature[day][0] + temperature[day][1]) / 2f;
            float tempDelta = temperature[day][1] - temperature[day][0];

            float rhAvg = (humidity[day][0] + humidity[day][1]) / 2f;
            float pressureAvg = (pressure[day][0] + pressure[day][1]) / 2f;

            float windStrength = (wind != null && wind.length > day) ? wind[day].baseSpeed() : 0f;

            // --- Base scoring ---
            if (pressureAvg < 1000f) stormScore += 0.3f;
            if (pressureAvg < 990f) stormScore += 0.2f;
            if (rhAvg > 0.8f) stormScore += 0.2f;
            if (tempDelta > 10f) stormScore += 0.15f;
            if (windStrength > 12f) stormScore += 0.1f;

            // --- Seasonal adjustment ---
            float seasonalMultiplier = getSeasonalStormMultiplier(level, pos);
            stormScore *= seasonalMultiplier;

            // --- Noise ---
            long seed = ProjectAtmosphere.seed ^ pos.asLong() ^ biome.hashCode() ^ day;
            Random rand = new Random(seed);
            stormScore += (rand.nextFloat() - 0.5f) * 0.1f;

            // --- Min/Max clamping ---
            float min = getSeasonalStormMin(level, pos);
            float max = getSeasonalStormMax(level, pos);
            stormWeek[day][0] = min+1;
            stormWeek[day][1] = clamp(stormScore, min, max)+1;
        }

        return stormWeek;
//        return new float[][] {
//            {7,7}, // Placeholder for the first day
//            {7,7}, // Placeholder for the second day
//            {7,7}, // Placeholder for the third day
//            {7,7}, // Placeholder for the fourth day
//            {7,7}, // Placeholder for the fifth day
//            {7,7}, // Placeholder for the sixth day
//            {7,7}  // Placeholder for the seventh day
        //};
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static float getSeasonalStormMultiplier(ServerLevel level, BlockPos pos) {
        int season = (int)((level.getDayTime() / 24000L) % 4);
        return switch (season) {
            case 0 -> 1.0f;  // Spring
            case 1 -> 1.3f;  // Summer
            case 2 -> 1.5f;  // Fall
            case 3 -> 0.7f;  // Winter
            default -> 1.0f;
        };
    }

    private static float getSeasonalStormMin(ServerLevel level, BlockPos pos) {
        int season = (int)((level.getDayTime() / 24000L) % 4);
        return switch (season) {
            case 1 -> 0.15f;
            case 2 -> 0.10f;
            case 3 -> 0.0f;
            default -> 0.05f;
        };
    }

    private static float getSeasonalStormMax(ServerLevel level, BlockPos pos) {
        int season = (int)((level.getDayTime() / 24000L) % 4);
        return switch (season) {
            case 1 -> 1.2f;
            case 2 -> 1.0f;
            case 3 -> 0.6f;
            default -> 0.9f;
        };
    }
}

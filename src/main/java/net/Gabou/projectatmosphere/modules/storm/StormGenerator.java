package net.Gabou.projectatmosphere.modules.storm;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.RegionBiomeSample;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.minecraft.core.BlockPos;

import java.util.Random;

public class StormGenerator {

    private static final float DEFAULT_MIN = 0.0f;
    private static final float DEFAULT_MAX = 1.0f;

    /**
     * Returns a 7-day storm profile, each day having [min, max] chance.
     */
    public static float[][] generateWeeklyStormProfile(
            RegionBiomeSample sample,
            float[][] temperature,
            float[][] humidity,
            float[][] pressure,
            WindVector[] wind,
            SeasonStage season
    ) {
        float[][] stormWeek = new float[7][2];
        BlockPos pos = sample.pos();

        for (int day = 0; day < 7; day++) {
            float stormScore = 0f;

            float tempAvg = (temperature[day][0] + temperature[day][1]) / 2f;
            float tempDelta = temperature[day][1] - temperature[day][0];

            float rhAvg = (humidity[day][0] + humidity[day][1]) / 2f;
            float pressureAvg = (pressure[day][0] + pressure[day][1]) / 2f;

            // Forecast generator intentionally uses the weekly forecast wind, not dynamic state wind.
            float windStrength = (wind != null && wind.length > day) ? wind[day].baseSpeed() : 0f;

            if (tempAvg > 24f) stormScore += 0.1f;
            if (tempAvg > 30f) stormScore += 0.15f;
            if (pressureAvg < 1008f) stormScore += 0.15f;
            if (pressureAvg < 1000f) stormScore += 0.25f;
            if (pressureAvg < 990f) stormScore += 0.2f;
            if (rhAvg > 0.6f) stormScore += 0.15f;
            if (rhAvg > 0.8f) stormScore += 0.15f;
            if (tempDelta > 8f) stormScore += 0.15f;
            if (windStrength > 10f) stormScore += 0.1f;
            if (windStrength > 14f) stormScore += 0.1f;

            float seasonalMultiplier = getSeasonalStormMultiplier(season);
            stormScore *= seasonalMultiplier;

            long seed = ProjectAtmosphere.seed ^ pos.asLong() ^ sample.biomeId().hashCode() ^ day;
            Random rand = new Random(seed);
            stormScore += (rand.nextFloat() - 0.5f) * 0.15f;

            float baseMin = getSeasonalStormMin(season);
            float baseMax = getSeasonalStormMax(season);

            float minJitter = (rand.nextFloat() - 0.5f) * 0.1f;
            float dailyMin = clamp(baseMin + minJitter, 0.0f, baseMax);

            float amplified = stormScore * 1.15f;
            float dailyMax = clamp(amplified, dailyMin, baseMax);

            if (dailyMax < 0.25f && rand.nextFloat() < 0.2f) {
                dailyMin = 0.0f;
                dailyMax = 0.0f;
            } else if (dailyMax < 0.45f && rand.nextFloat() < 0.25f) {
                dailyMin = Math.max(dailyMin, 0.18f);
                dailyMax = Math.max(dailyMax, 0.30f);
            }

            stormWeek[day][0] = dailyMin;
            stormWeek[day][1] = dailyMax;
        }

        return stormWeek;
        }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static float getSeasonalStormMultiplier(SeasonStage season) {
        return switch (season) {
            case SPRING -> 1.1f;
            case SUMMER -> 1.4f;
            case AUTUMN -> 1.6f;
            case WINTER -> 0.8f;
            case NEUTRAL -> 1.0f;
        };
    }

    private static float getSeasonalStormMin(SeasonStage season) {
        return switch (season) {
            case SPRING -> 0.18f;
            case SUMMER -> 0.12f;
            case AUTUMN -> 0.0f;
            case WINTER -> 0.08f;
            case NEUTRAL -> 0.12f;
        };
    }

    private static float getSeasonalStormMax(SeasonStage currentSeason) {
        return switch (currentSeason) {
            case SPRING -> 0.9f;
            case SUMMER, AUTUMN -> 1.0f;
            case WINTER -> 0.7f;
            case NEUTRAL -> 0.9f;
        };
    }

}

package net.Gabou.projectatmosphere.modules.humidity;

import java.util.Random;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.region.RegionBiomeSample;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/**
 * Generates humidity forecasts from backend climate state and temperature forecasts.
 */
public class HumidityGenerator {
    public static final float MAX_HUMIDITY = 100f;
    public static final float MIN_HUMIDITY_TROPICAL_BIOME = 75f;
    public static final float MIN_VANILLA_TEMP_TROPICAL_BIOME = 1.8f;
    public static final float MIN_HUMIDITY_DESERT_BIOME = 5f;

    public static float[][] generateWeekForecast(ServerLevel level, RegionBiomeSample sample, float[][] tempWeek, long day) {
        float baseRh = AsyncAtmosphereService.callOnMainThread(() -> {
            Biome biome = level.getBiome(sample.pos()).get();
            float rh = biome.getModifiedClimateSettings().downfall() * MAX_HUMIDITY;
            if (!biome.getModifiedClimateSettings().hasPrecipitation()) {
                rh = MIN_HUMIDITY_DESERT_BIOME;
            } else if (biome.getBaseTemperature() > MIN_VANILLA_TEMP_TROPICAL_BIOME) {
                rh = Math.max(rh, MIN_HUMIDITY_TROPICAL_BIOME);
            }
            return rh;
        });

        long seed = ProjectAtmosphere.seed ^ sample.pos().asLong() ^ sample.biomeId().hashCode() ^ day;
        Random rand = new Random(seed);

        if (tempWeek == null) {
            return new float[7][2];
        }

        float[][] humWeek = new float[7][2];
        for (int d = 0; d < 7; d++) {
            float minTemp = tempWeek[d][0];
            float maxTemp = tempWeek[d][1];
            float deltaT = maxTemp - minTemp;
            float humiditySwing = Math.min(20f, deltaT * 2.5f);
            float noise = 2f * (rand.nextFloat() - 0.5f);

            float min = Math.max(MIN_HUMIDITY_DESERT_BIOME, baseRh - humiditySwing * 0.5f) + noise;
            float max = Math.max(MIN_HUMIDITY_DESERT_BIOME, baseRh + humiditySwing * 0.5f) + noise;
            humWeek[d][0] = Math.max(0f, Math.min(min, MAX_HUMIDITY));
            humWeek[d][1] = Math.max(0f, Math.min(max, MAX_HUMIDITY));
        }

        return humWeek;
    }
}

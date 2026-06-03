
package net.Gabou.projectatmosphere.modules.humidity;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.Random;

/**
 * Generates humidity forecasts from backend climate state and temperature forecasts.
 */
public class HumidityGenerator {

    private static final float CELSIUS_TO_KELVIN = 273.15f;
    public static final float MAX_HUMIDITY = 100f;
    public static final float MIN_HUMIDITY_TROPICAL_BIOME = 75f;
    public static final float MIN_VANILLA_TEMP_TROPICAL_BIOME = 1.8f;
    public static final float MIN_HUMIDITY_DESERT_BIOME = 5f;

    // ---------------------------------------------------------------------
    // Forecast generation
    // ---------------------------------------------------------------------
    public static float[][] generateWeekForecast(ServerLevel level, BiomeInstanceKey b, Long day) {
        BlockPos pos = b.samplePos();
        ResourceLocation biomeId = b.biomeType();

        // Step 1: fetch unsafe data on main
        BiomeData baseData = AsyncAtmosphereService.callOnMainThread(() -> {
            Biome biome = level.getBiome(pos).get();
            float baseRH = biome.getModifiedClimateSettings().downfall() * MAX_HUMIDITY;

            if (!biome.getModifiedClimateSettings().hasPrecipitation()) {
                baseRH = MIN_HUMIDITY_DESERT_BIOME;
            } else if (biome.getBaseTemperature() > MIN_VANILLA_TEMP_TROPICAL_BIOME) {
                baseRH = Math.max(baseRH, MIN_HUMIDITY_TROPICAL_BIOME);
            }

            float[][] tempWeek = ForecastGenerator.getClosestValidForecast(b, ForecastType.TEMPERATURE).getTemperature();
            return new BiomeData(baseRH, tempWeek);
        });

        // Step 2: do heavy math async
        long seed = ProjectAtmosphere.seed ^ pos.asLong() ^ biomeId.hashCode() ^ day;
        Random rand = new Random(seed);

        if (baseData.tempWeek == null) return new float[7][2];

        float[][] humWeek = new float[7][2];
        for (int d = 0; d < 7; d++) {
            float Tmin = baseData.tempWeek[d][0];
            float Tmax = baseData.tempWeek[d][1];
            float deltaT = Tmax - Tmin;

            float humiditySwing = Math.min(20f, deltaT * 2.5f);

            float finalMin = Math.max(MIN_HUMIDITY_DESERT_BIOME, baseData.baseRH - humiditySwing * 0.5f);
            float finalMax = Math.max(MIN_HUMIDITY_DESERT_BIOME, baseData.baseRH + humiditySwing * 0.5f);

            float noise = 2f * (rand.nextFloat() - 0.5f);
            finalMin += noise;
            finalMax += noise;

            humWeek[d][0] = Math.max(0f, Math.min(finalMin, MAX_HUMIDITY));
            humWeek[d][1] = Math.max(0f, Math.min(finalMax, MAX_HUMIDITY));
        }

        return humWeek;
    }

    private record BiomeData(float baseRH, float[][] tempWeek) {}
}

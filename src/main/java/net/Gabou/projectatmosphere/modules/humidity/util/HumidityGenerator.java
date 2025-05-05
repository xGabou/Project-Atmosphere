// src/main/java/net/Gabou/projectatmosphere/modules/humidity/util/HumidityGenerator.java
package net.Gabou.projectatmosphere.modules.humidity.util;

import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.Random;

public class HumidityGenerator {

    private static final float CELSIUS_TO_KELVIN = 273.15f;

    /** Saturation vapor pressure (Magnus–Tetens) in hPa. */
    private static double satVaporPressure(float T) {
        return 6.112 * Math.exp((17.67 * T) / (T + 243.5));
    }

    public static float[][] generateWeekForecast(ServerLevel world, BlockPos pos, ResourceLocation biomeId) {
        long day = world.getDayTime() / 24000L;
        long seed = world.getSeed() ^ pos.asLong() ^ biomeId.hashCode() ^ day;
        Random rand = new Random(seed);

        Biome biome = world.getBiome(pos).get();
        float baseRH = biome.getModifiedClimateSettings().downfall() * 100f;

        // Clamp RH for desert and tropical biomes
        if (!biome.getModifiedClimateSettings().hasPrecipitation()) {
            baseRH = Math.min(baseRH, 25f);
        } else if (biome.getBaseTemperature() > 1.8f) {
            baseRH = Math.max(baseRH, 75f);
        }

        float[][] tempWeek = TemperatureProfileManager.getWeeklyForecast(biomeId);
        if (tempWeek == null) return new float[7][2];

        float[][] humWeek = new float[7][2];
        for (int d = 0; d < 7; d++) {
            float Tmin = tempWeek[d][0];
            float Tmax = tempWeek[d][1];
            float Tmean = (Tmin + Tmax) * 0.5f;

            double es = satVaporPressure(Tmean);
            double evpMin = satVaporPressure(Tmin);
            double evpMax = satVaporPressure(Tmax);

            float RHmin = (float) (evpMin / es * 100f);
            float RHmax = (float) (evpMax / es * 100f);

            float blend = 0.6f;
            float finalMin = baseRH * (1 - blend) + RHmin * blend;
            float finalMax = baseRH * (1 - blend) + RHmax * blend;

            float noise = 2f * (rand.nextFloat() - 0.5f);
            finalMin += noise;
            finalMax += noise;

            humWeek[d][0] = Math.max(0f, Math.min(finalMin, 100f));
            humWeek[d][1] = Math.max(0f, Math.min(finalMax, 100f));
        }

        return humWeek;
    }
}

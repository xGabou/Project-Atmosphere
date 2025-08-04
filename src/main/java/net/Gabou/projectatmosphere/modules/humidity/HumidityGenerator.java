// src/main/java/net/Gabou/projectatmosphere/modules/humidity/util/HumidityGenerator.java
package net.Gabou.projectatmosphere.modules.humidity;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Objects;
import java.util.Random;

public class HumidityGenerator {

    private static final float CELSIUS_TO_KELVIN = 273.15f;

//    /**
//     * Saturation vapor pressure (Magnus–Tetens) in hPa.
//     */
//    private static double satVaporPressure(float T) {
//        return 6.112 * Math.exp((17.67 * T) / (T + 243.5));
//    }

    public static float[][] generateWeekForecast(Level world, BiomeInstanceKey b) {
        ResourceLocation biomeId = b.biomeType();
        if (Objects.equals(biomeId, ResourceLocation.fromNamespaceAndPath("minecraft", "desert"))) {
            ProjectAtmosphere.LOGGER.info("Desert biome detected, using default humidity values.");
        }
        BlockPos pos = b.samplePos();
        long day = world.getDayTime() / 24000L;
        long seed = ProjectAtmosphere.seed ^ pos.asLong() ^ biomeId.hashCode() ^ day;
        Random rand = new Random(seed);

        Biome biome = world.getBiome(pos).get();
        float baseRH = biome.getModifiedClimateSettings().downfall() * 100f;

        // Clamp RH for desert and tropical biomes
        if (!biome.getModifiedClimateSettings().hasPrecipitation()) {
            baseRH = Math.max(5f, Math.min(baseRH, 15f));  // for arid
        } else if (biome.getBaseTemperature() > 1.8f) {
            baseRH = Math.max(baseRH, 80f);
        }

        float[][] tempWeek = ForecastGenerator.getClosestValidForecast(b, ForecastType.TEMPERATURE).getTemperature();
        if (tempWeek == null) return new float[7][2];

        float[][] humWeek = new float[7][2];
        for (int d = 0; d < 7; d++) {
            float Tmin = tempWeek[d][0];
            float Tmax = tempWeek[d][1];
            float deltaT = Tmax - Tmin;

            float humiditySwing = Math.min(20f, deltaT * 2.5f); // up to ±10%

            float finalMin = Math.max(5f, baseRH - humiditySwing * 0.5f);
            float finalMax = Math.max(5f, baseRH + humiditySwing * 0.5f);


            float noise = 2f * (rand.nextFloat() - 0.5f);
            finalMin += noise;
            finalMax += noise;


            humWeek[d][0] = Math.max(0f, Math.min(finalMin, 100f));
            humWeek[d][1] = Math.max(0f, Math.min(finalMax, 100f));

        }

        return humWeek;
    }
}

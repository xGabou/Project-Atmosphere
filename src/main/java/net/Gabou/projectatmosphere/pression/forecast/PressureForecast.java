package net.Gabou.projectatmosphere.pression.forecast;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.temperature.util.TemperatureProfileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PressureForecast {

    /**
     * Scans a square of side 2*radius blocks around `center`, finds one sample per biome,
     * then builds a 7-day pressure forecast from altitude, temperature, humidity and storm spikes.
     */
    public static Map<ResourceLocation, double[]> generateForecastAround(Level world,
                                                                         BlockPos center,
                                                                         int radius) {
        // 1) gather biome sample positions
        Set<ResourceLocation> foundBiomes = new HashSet<>();
        Map<ResourceLocation, BlockPos> biomeSamples = new HashMap<>();
        AtmosphereUtils.findBiomes(world, center, radius, foundBiomes, biomeSamples);

        // 2) for each sampled biome compute pressure curve
        Map<ResourceLocation, double[]> forecast = new HashMap<>();
        for (Map.Entry<ResourceLocation, BlockPos> entry : biomeSamples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos samplePos      = entry.getValue();

            // altitude → base pressure (hPa)
            double altitude = samplePos.getY();
            double P0       = 1013.25 * Math.exp(-altitude / 8400.0);

            // pull the 7×2 (min/max) temperature forecast
            float[][] tempWeek = TemperatureProfileManager.getWeeklyForecast(biome);
            if (tempWeek == null) continue;

            double[] weekPress = new double[7];
            for (int d = 0; d < 7; d++) {
                double Tmid    = (tempWeek[d][0] + tempWeek[d][1]) / 2.0;
                double deltaT  = -0.5 * (Tmid - 15.0);            // ~0.5 hPa per °C from 15°C

                double RH      = HumidityManager.getAverageHumidity(biome, d);
                double deltaH  = -0.05 * RH;                     // ~0.05 hPa per %RH

                double spike   = StormManager.randomStormSpike(biome, d); // ±10 hPa

                weekPress[d]   = P0 + deltaT + deltaH + spike;
            }

            forecast.put(biome, weekPress);
        }

        return forecast;
    }
}

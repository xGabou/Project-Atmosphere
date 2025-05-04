// src/main/java/net/Gabou/projectatmosphere/modules/pressure/forecast/PressureForecast.java
package net.Gabou.projectatmosphere.modules.pressure.forecast;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.modules.storm.manager.StormManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PressureForecast {

    /**
     * Scans a square (2*radius), finds one sample per biome,
     * then builds a 7-day pressure forecast using altitude, temp, humidity & storm spikes.
     */
    public static Map<ResourceLocation, float[]> generateForecastAround(Level world,
                                                                         BlockPos center,
                                                                         int radius) {
        Set<ResourceLocation> found = new HashSet<>();
        Map<ResourceLocation, BlockPos> samples = new HashMap<>();
        AtmosphereUtils.findBiomes(world, center, radius, found, samples);

        Map<ResourceLocation, float[]> forecast = new HashMap<>();
        for (var e : samples.entrySet()) {
            ResourceLocation biome = e.getKey();
            BlockPos pos = e.getValue();
            float altitude = pos.getY();
            float P0 = (float) (1013.25 * Math.exp(-altitude / 8400.0));

            float[][] tempWeek = TemperatureProfileManager.getWeeklyForecast(biome);
            if (tempWeek == null) continue;

            float[] weekPress = new float[7];
            for (int d = 0; d < 7; d++) {
                float Tmid = (float) ((tempWeek[d][0] + tempWeek[d][1]) / 2.0);
                float deltaT = (float) (-0.5 * (Tmid - 15.0));            // ≈0.5 hPa per °C
                float RH = HumidityManager.getAverageHumidity(biome, d);    // 0–100%
                float deltaH = (float) (-0.05 * RH);                     // ≈0.05 hPa per %RH
                float spike = (float) StormManager.randomStormSpike(biome, d); // ±10 hPa
                weekPress[d] = P0 + deltaT + deltaH + spike;
            }
            forecast.put(biome, weekPress);
        }
        return forecast;
    }
}

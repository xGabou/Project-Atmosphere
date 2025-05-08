package net.Gabou.projectatmosphere.modules.wind.util;

import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.AtmosphericPhysics;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;
import java.util.Set;

/**
 * Utility for generating weekly wind speed forecasts.
 * Values are in meters per second (m/s).
 */
public class WindGenerator {

    private static final Random random = new Random();
    private static final float SPEED_SCALING = 3.0f;
    private static final float VARIATION_STRENGTH = 2.0f;

    public static float[] generateBaseWindWeek(ServerLevel world, BlockPos center, ResourceLocation biome, BiomeInstanceKey selfKey) {
        float[] baseWind = new float[7];
        float[][] selfPressure = PressureProfileManager.getWeeklyForecast(selfKey);
        float[][] selfTemp = TemperatureProfileManager.getWeeklyForecast(selfKey);
        float[][] selfHumidity = HumidityProfileManager.getWeeklyForecast(selfKey);

        Set<BiomeInstanceKey> neighbors = AtmosphereUtils.findBiomes(world, center, 120);
        float altitude = center.getY();
        float biomeFactor = getBiomeWindModifier(biome);
        double[] airDensity = AtmosphericPhysics.computeAirDensity(selfTemp, selfHumidity);

        for (int d = 0; d < 7; d++) {
            float Pself = (selfPressure[d][0] + selfPressure[d][1]) * 0.5f;
            float Pavg = 0f, count = 0;

            for (BiomeInstanceKey key : neighbors) {
                float[][] p = PressureProfileManager.getWeeklyForecast(key);
                if (p != null) {
                    float Pn = (p[d][0] + p[d][1]) * 0.5f;
                    Pavg += Pn;
                    count++;
                }
            }

            if (count == 0) {
                baseWind[d] = 5f; // fallback
                continue;
            }

            Pavg /= count;
            float dP = Math.abs(Pavg - Pself); // pressure gradient in hPa

            float Tavg = (selfTemp[d][0] + selfTemp[d][1]) * 0.5f;
            float RHavg = (selfHumidity[d][0] + selfHumidity[d][1]) * 0.5f;
            float tempFactor = 1.0f + Tavg / 40f; // 0.8 to 1.3
            float humidityFactor = 1.0f + (RHavg - 50f) / 200f;
            float densityFactor = (float) (airDensity[d] / 1.225f); // standard = 1.0

            float altitudeFactor = (float) Math.log(1 + altitude / 10f);

            float speed = dP * 3.2f * tempFactor * humidityFactor * densityFactor * biomeFactor * altitudeFactor * SPEED_SCALING;

            // Clamp to reasonable base values (in m/s)
            baseWind[d] = Math.max(6f, Math.min(speed, 50f)); // ~21.6–79.2 km/h
        }

        return baseWind;
    }



    /**
     * Defines biome-based surface drag or amplification.
     * Lower values simulate friction (e.g., forests), higher = open air.
     */
    private static float getBiomeWindModifier(ResourceLocation biome) {
        String path = biome.getPath();

        if (path.contains("forest") || path.contains("taiga") || path.contains("jungle")) {
            return 0.7f;
        }
        if (path.contains("plains") || path.contains("savanna")) {
            return 1.1f;
        }
        if (path.contains("ocean") || path.contains("beach")) {
            return 1.25f;
        }
        if (path.contains("mountain") || path.contains("peak") || path.contains("windswept")) {
            return 1.4f;
        }

        return 1.0f; // default
    }
}

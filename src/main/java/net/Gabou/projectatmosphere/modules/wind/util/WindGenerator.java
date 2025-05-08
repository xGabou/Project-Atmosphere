package net.Gabou.projectatmosphere.modules.wind.util;

import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

/**
 * Utility for generating weekly wind speed forecasts.
 * Values are in meters per second (m/s).
 */
public class WindGenerator {

    private static final Random random = new Random();
    private static final float SPEED_SCALING = 1.0f;
    private static final float VARIATION_STRENGTH = 2.0f;

    /**
     * Generates a 7-day wind speed forecast.
     * @param world The server world.
     * @param pos The position (altitude affects wind).
     * @param biome The biome type.
     * @param entry The biome instance key.
     * @return 7x2 array: [day][0] = min wind speed, [day][1] = max wind speed
     */
    public static float[][] generateWeeklyWindProfile(ServerLevel world, BlockPos pos, ResourceLocation biome, BiomeInstanceKey entry) {
        float[][] forecast = new float[7][2];
        float[][] pressureForecast = PressureProfileManager.getWeeklyForecast(entry);

        float altitude = pos.getY();
        float altitudeFactor = (float) Math.log(1 + altitude / 10.0f); // increases with elevation

        for (int day = 0; day < 7; day++) {
            float minPressure = pressureForecast[day][0];
            float maxPressure = pressureForecast[day][1];
            float pressureGradient = Math.abs(maxPressure - minPressure);

            float baseSpeed = SPEED_SCALING * pressureGradient * altitudeFactor;
            float variation = random.nextFloat() * VARIATION_STRENGTH;

            float minSpeed = Math.max(0f, baseSpeed - variation);
            float maxSpeed = baseSpeed + variation;

            forecast[day][0] = minSpeed;
            forecast[day][1] = maxSpeed;
        }

        return forecast;
    }
}

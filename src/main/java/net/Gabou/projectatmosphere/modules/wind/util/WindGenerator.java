package net.Gabou.projectatmosphere.modules.wind.util;

import net.Gabou.projectatmosphere.modules.pressure.util.PressureStorageManager;
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
    private static final float valueConst = 1f;
    private static final float variationStrenght = 2.0f;

    public static float[][] generateWeeklyWindProfile(ServerLevel world, BlockPos pos, ResourceLocation biome, BiomeInstanceKey entry) {

        float[][] forecast = new float[7][2];
        float[][] pressureForecast = PressureStorageManager.getForecast(entry);
        for (int i = 0; i < 7; i++) {
            float minPressure = pressureForecast[i][0];
            float maxPressure = pressureForecast[i][1];
            float altitude = pos.getY();
            float altitudeFactor = (float) Math.log(1 + altitude / 10);
            float baseSpeed = valueConst * (maxPressure - minPressure) * altitudeFactor;
            float variation = random.nextFloat() * variationStrenght;
            float minSpeed = baseSpeed - variation;
            float maxSpeed = baseSpeed + variation;

            forecast[i][0] = minPressure;
            forecast[i][1] = minSpeed;
        }

        return forecast;
    }
}

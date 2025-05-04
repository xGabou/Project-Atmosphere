package net.Gabou.projectatmosphere.modules.wind.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class WindManager {


    public static final int WIND_SPEED = 10; // in m/s
    public static final int WIND_DIRECTION = 0; // in degrees
    public static final int WIND_GUSTS = 20; // in m/s

    public static void updateWind() {
        // Update wind data here
    }

    public static void resetWind() {
        // Reset wind data here
    }

    public static float randomWindSpeed(ResourceLocation biome, BlockPos pos, int d) {
        // Generate a random wind speed based on the biome and day
        float baseSpeed = getBaseWindSpeed(biome,pos);
        // For simplicity, we will just return a random value between 0 and WIND_SPEED
        return (float) (Math.random() * baseSpeed) + (float) (Math.random[0,10] * WIND_SPEED);
    }

    private static float getBaseWindSpeed(ResourceLocation biome, BlockPos pos) {
        // Get the base wind speed for the given biome and position
        // For simplicity, we will just return a constant value
        return WIND_SPEED;

    }
}

package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.core.BlockPos;

public class WindMath {

    /**
     * Returns a linear gust factor between 0 and 1 based on world time.
     * - Returns 1.0 during peak gusts
     * - Returns 0.0 during calm
     * - Smooth sinusoidal transition
     */
    public static float computeGustFactor(long worldTime) {
        
        float wave = (float) Math.sin((worldTime % 1000L) / 100.0);
        return 0.5f + 0.5f * wave; 
    }

    /**
     * Uses the gust factor to smoothly interpolate between base and gust speed.
     */
    public static float getSmoothGustedSpeed(WindVector vector, long worldTime) {
        float gustFactor = computeGustFactor(worldTime);
        return vector.baseSpeed() + (vector.gustSpeed() - vector.baseSpeed()) * gustFactor;
    }

    /**
     * Returns a BlockPos offset based on the wind direction.
     * - Positive X for east, negative X for west
     * - Positive Z for south, negative Z for north
     */
    public static BlockPos getWindOffset(WindVector wind) {
        float angle = wind.angleRadians(); 
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);

        int offsetX = (int) Math.signum(dx);
        int offsetZ = (int) Math.signum(dz);
        return new BlockPos(offsetX, 0, offsetZ);
    }

}

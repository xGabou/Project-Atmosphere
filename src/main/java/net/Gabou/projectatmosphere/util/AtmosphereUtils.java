package net.Gabou.projectatmosphere.util;

import net.minecraft.world.phys.Vec3;
import java.util.Random;

public class AtmosphereUtils {
    public static Vec3 randomDrift(Random random, double speed) {
        double dx = (random.nextDouble() - 0.5) * speed;
        double dz = (random.nextDouble() - 0.5) * speed;
        return new Vec3(dx, 0, dz);
    }
}

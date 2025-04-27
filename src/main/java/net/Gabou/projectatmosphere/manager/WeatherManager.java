package net.Gabou.projectatmosphere.manager;

import net.minecraft.world.level.Level;

public class WeatherManager {
    private static final float BASE_SPAWN_CHANCE = 0.01f;

    public static float getCloudSpawnChance(Level level) {
        if (level.isThundering()) {
            return BASE_SPAWN_CHANCE * 5.0f;
        } else if (level.isRaining()) {
            return BASE_SPAWN_CHANCE * 2.5f;
        } else {
            return BASE_SPAWN_CHANCE;
        }
    }
}

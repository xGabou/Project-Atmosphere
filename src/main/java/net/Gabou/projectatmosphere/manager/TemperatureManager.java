package net.Gabou.projectatmosphere.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class TemperatureManager {
    public static float getEffectiveTemperature(Level level, double x, double y, double z) {
        Biome biome = level.getBiome(new BlockPos((int) x, (int) y, (int) z)).value();
        return biome.getBaseTemperature();
    }
}

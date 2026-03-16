package net.Gabou.projectatmosphere.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Weather-driven world effect hook for modders.
 */
public interface AtmosphereWorldEffect {
    String id();

    void tick(ServerLevel level, RandomSource random, BlockPos origin, WeatherSnapshot snapshot);
}

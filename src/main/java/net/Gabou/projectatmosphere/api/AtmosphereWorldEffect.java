package net.Gabou.projectatmosphere.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Weather-driven world effect contract for modders.
 * Implementations should read the provided snapshot and apply only local world effects.
 */
public interface AtmosphereWorldEffect {
    String id();

    void tick(ServerLevel level, RandomSource random, BlockPos origin, WeatherSnapshot snapshot);
}

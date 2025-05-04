package net.Gabou.projectatmosphere.modules.temperature.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface TemperatureProvider {
    float getTemperature(ServerLevel level, BlockPos pos);
}


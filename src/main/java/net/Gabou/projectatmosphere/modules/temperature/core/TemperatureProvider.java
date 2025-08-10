package net.Gabou.projectatmosphere.modules.temperature.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface TemperatureProvider {

    /**
     * Provides the temperature for a block position within the given level.
     *
     * @param level the server level to sample
     * @param pos   the block position being evaluated
     * @return the temperature at the specified position
     */
    float getTemperature(ServerLevel level, BlockPos pos);
}


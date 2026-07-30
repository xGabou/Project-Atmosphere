package net.Gabou.projectatmosphere.seasons;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Pluggable season provider so Project Atmosphere can listen to Serene Seasons,
 * TFC, or any other mod without hard dependencies.
 */
public interface SeasonProvider {
    String id();

    /**
     * Return the current snapshot for the given level. Should never be null;
     * return {@link SeasonSnapshot#neutral()} if data is unavailable.
     */
    SeasonSnapshot snapshot(Level level);

    default SeasonSnapshot snapshot(Level level, BlockPos pos) {
        return snapshot(level);
    }
}

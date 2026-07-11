package net.Gabou.projectatmosphere.seasons;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public interface SeasonTimeDelegate {
    default String providerId() {
        return getClass().getName();
    }

    SeasonSnapshot snapshot(Level level);

    default SeasonSnapshot snapshot(Level level, BlockPos pos) {
        return snapshot(level);
    }

    long seasonCycleTicks(Level level);

    long seasonDuration(Level level);

    long dayDuration(Level level);

    default void onRainStarted(ServerLevel level, int externalCloudRegionId) {
    }

    default void onRainEnded(ServerLevel level, int externalCloudRegionId) {
    }
}

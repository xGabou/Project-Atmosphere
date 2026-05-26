package net.Gabou.projectatmosphere.seasons;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public interface SeasonTimeDelegate {
    default String providerId() {
        return getClass().getName();
    }

    SeasonSnapshot snapshot(Level level);

    long seasonCycleTicks(Level level);

    long seasonDuration(Level level);

    long dayDuration(Level level);

    default void onRainStarted(ServerLevel level, CloudRegion cloudRegion) {
    }

    default void onRainEnded(ServerLevel level, CloudRegion cloudRegion) {
    }
}

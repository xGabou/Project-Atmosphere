package net.Gabou.projectatmosphere.api.event;

import net.Gabou.projectatmosphere.api.WeatherSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/**
 * Read-only weather sampling event fired during Project Atmosphere weather ticks.
 * Listeners should inspect the snapshot rather than mutate weather state.
 */
public class AtmosphereWeatherTickEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final WeatherSnapshot snapshot;

    public AtmosphereWeatherTickEvent(ServerLevel level, BlockPos pos, WeatherSnapshot snapshot) {
        this.level = level;
        this.pos = pos;
        this.snapshot = snapshot;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public WeatherSnapshot getSnapshot() {
        return snapshot;
    }
}

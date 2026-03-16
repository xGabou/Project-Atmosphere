package net.Gabou.projectatmosphere.api.event;

import net.Gabou.projectatmosphere.api.WeatherSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * Fired during Project Atmosphere weather sampling ticks.
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

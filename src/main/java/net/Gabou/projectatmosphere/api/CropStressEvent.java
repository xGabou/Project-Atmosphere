package net.Gabou.projectatmosphere.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

import java.util.EnumSet;

/**
 * Event fired when the environment around a position indicates crop stress.
 * Listeners can use this to apply custom crop logic or integrate with
 * other mods.
 */
public class CropStressEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final EnumSet<CropStressType> stresses;
    private final float temperature;
    private final float humidity;

    public CropStressEvent(ServerLevel level, BlockPos pos, EnumSet<CropStressType> stresses, float temperature, float humidity) {
        this.level = level;
        this.pos = pos;
        this.stresses = stresses;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public EnumSet<CropStressType> getStresses() {
        return stresses;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getHumidity() {
        return humidity;
    }
}


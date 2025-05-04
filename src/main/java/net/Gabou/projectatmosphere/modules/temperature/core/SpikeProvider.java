package net.Gabou.projectatmosphere.modules.temperature.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public interface SpikeProvider {
    void clearSpikes(ServerLevel level);
    void applyOngoingSpike(ServerLevel level, ResourceLocation biome);
    void startNewSpike(ServerLevel level, ResourceLocation biome, int magnitude);
}

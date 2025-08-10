package net.Gabou.projectatmosphere.modules.temperature.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public interface SpikeProvider {

    /**
     * Clears any temperature spikes present in the given level.
     *
     * @param level the server level whose spikes will be cleared
     */
    void clearSpikes(ServerLevel level);

    /**
     * Applies any ongoing spike effects for the specified biome in the level.
     *
     * @param level the server level to modify
     * @param biome the biome identifier undergoing a spike
     */
    void applyOngoingSpike(ServerLevel level, ResourceLocation biome);

    /**
     * Starts a new spike for the given biome with a certain magnitude.
     *
     * @param level     the server level to modify
     * @param biome     the biome identifier undergoing the spike
     * @param magnitude the intensity of the spike
     */
    void startNewSpike(ServerLevel level, ResourceLocation biome, int magnitude);
}

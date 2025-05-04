package net.Gabou.projectatmosphere.modules.temperature.spike;

import net.minecraft.resources.ResourceLocation;

public class SpikeData {
    public final ResourceLocation biome;
    public final float[][] week;
    public final SpikeState state;

    public SpikeData(ResourceLocation biome, float[][] week, SpikeState state) {
        this.biome = biome;
        this.week = week;
        this.state = state;
    }
}

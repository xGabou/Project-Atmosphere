package net.Gabou.projectatmosphere.modules.region;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Temporary biome sample used while generating or migrating a region forecast.
 * Runtime identity remains the owning RegionInstanceKey.
 */
public record RegionBiomeSample(ResourceLocation biomeId, BlockPos pos, int weight) {
    public RegionBiomeSample {
        weight = Math.max(1, weight);
    }
}

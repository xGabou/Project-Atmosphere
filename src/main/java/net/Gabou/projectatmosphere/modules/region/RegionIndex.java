package net.Gabou.projectatmosphere.modules.region;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Mapping helper from world positions to forecast regions.
 */
public interface RegionIndex {
    RegionInstanceKey regionFor(BlockPos pos, ResourceKey<Level> dimension);
}

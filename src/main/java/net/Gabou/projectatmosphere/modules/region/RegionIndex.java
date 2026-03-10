package net.Gabou.projectatmosphere.modules.region;

import java.util.List;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Mapping helper from world positions to forecast regions and their biome membership.
 */
public interface RegionIndex {
    RegionInstanceKey regionFor(BlockPos pos, ResourceKey<Level> dimension);

    List<BiomeInstanceKey> biomesFor(RegionInstanceKey id);
}

package net.Gabou.projectatmosphere.modules.region;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * RegionIndex backed by the existing RegionInstanceKey grid (2000 block cells) and a cached region->biomes mapping.
 */
public final class GridRegionIndex implements RegionIndex {
    private final int regionSize;

    public GridRegionIndex() {
        this(RegionInstanceKey.DEFAULT_REGION_SIZE);
    }

    public GridRegionIndex(int regionSize) {
        this.regionSize = regionSize;
    }

    @Override
    public RegionInstanceKey regionFor(BlockPos pos, ResourceKey<Level> dimension) {
        return RegionInstanceKey.from(pos, regionSize);
    }
}

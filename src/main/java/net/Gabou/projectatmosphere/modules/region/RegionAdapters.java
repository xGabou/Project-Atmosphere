package net.Gabou.projectatmosphere.modules.region;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers to bridge legacy biome/region keys and region-local math.
 */
public final class RegionAdapters {
    private RegionAdapters() {
    }

    /**
     * Convert world position to region-local coordinates (0..regionSize for X/Z, Y kept as is).
     */
    public static Vec3 toRegionLocal(BlockPos pos, RegionInstanceKey legacy) {
        int minX = legacy.regionX() * legacy.regionSize();
        int minZ = legacy.regionZ() * legacy.regionSize();
        return new Vec3(pos.getX() - minX, pos.getY(), pos.getZ() - minZ);
    }
}

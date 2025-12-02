package net.Gabou.projectatmosphere.modules.region;

import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers to bridge legacy biome/region keys to the new ForecastRegionId API.
 */
public final class RegionAdapters {
    private RegionAdapters() {
    }

    public static ForecastRegionId fromRegionInstance(RegionInstanceKey legacy, ResourceKey<Level> dim) {
        return new ForecastRegionId(legacy.regionX(), legacy.regionZ(), dim);
    }

    public static ForecastRegionId fromPosition(BlockPos pos, ResourceKey<Level> dim) {
        RegionInstanceKey legacy = RegionInstanceKey.from(pos);
        return fromRegionInstance(legacy, dim);
    }

    public static ForecastRegionId fromBiomeKey(BiomeInstanceKey biome, ResourceKey<Level> dim) {
        return fromPosition(biome.samplePos(), dim);
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

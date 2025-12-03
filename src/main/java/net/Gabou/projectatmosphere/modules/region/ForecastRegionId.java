package net.Gabou.projectatmosphere.modules.region;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Identifier for a forecast region. Uses region-space coordinates and a dimension key.
 */
public record ForecastRegionId(int rx, int rz, ResourceKey<Level> dimension) {
    private static final int CHUNKS_PER_REGION = 8;

    public static ForecastRegionId ofChunk(int chunkX, int chunkZ, ResourceKey<Level> dim) {
        return new ForecastRegionId(chunkX >> 3, chunkZ >> 3, dim);
    }

    public BlockPos toCenterBlockPos() {
        int blockX = (rx * CHUNKS_PER_REGION * 16) + (CHUNKS_PER_REGION * 16 / 2);
        int blockZ = (rz * CHUNKS_PER_REGION * 16) + (CHUNKS_PER_REGION * 16 / 2);
        return new BlockPos(blockX, 0, blockZ);
    }
}

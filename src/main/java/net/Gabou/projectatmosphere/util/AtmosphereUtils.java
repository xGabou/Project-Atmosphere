package net.Gabou.projectatmosphere.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class AtmosphereUtils {


    public static Vec3 randomDrift(Random random, double speed) {
        double dx = (random.nextDouble() - 0.5) * speed;
        double dz = (random.nextDouble() - 0.5) * speed;
        return new Vec3(dx, 0, dz);
    }
    /**
     * Scans the square area around `center` in steps of 16 blocks, and for each
     * newly encountered biome adds its key to `foundBiomes` and records the exact
     * sample position in `biomeSamples`.
     *
     * @param world          the world to sample in
     * @param center         the central BlockPos to scan around
     * @param radiusBlocks   how far (in blocks) to scan in each horizontal direction
     * @param foundBiomes    a mutable set to which discovered biome ResourceLocations are added
     * @param biomeSamples   a mutable map from biome ResourceLocation → one representative BlockPos
     */
    public static void findBiomes(Level world,
                                  BlockPos center,
                                  int radiusBlocks,
                                  Set<ResourceLocation> foundBiomes,
                                  Map<ResourceLocation, BlockPos> biomeSamples) {
        int step = 16;
        for (int dx = -radiusBlocks; dx <= radiusBlocks; dx += step) {
            for (int dz = -radiusBlocks; dz <= radiusBlocks; dz += step) {
                BlockPos pos = center.offset(dx, 0, dz);
                ResourceLocation biomeKey = world.getBiome(pos)
                        .unwrapKey()
                        .get()
                        .location();
                if (foundBiomes.add(biomeKey)) {
                    biomeSamples.put(biomeKey, pos);
                }
            }
        }
    }
    public static Path getPerWorldSavePath(ServerLevel world, String fileName) {
        return world.getServer()
                .getWorldPath(LevelResource.ROOT) // this gives saves/New World/
                .resolve(world.dimension().location().getNamespace().equals("minecraft")
                        ? world.dimension().location().getPath() // e.g., "DIM1", "DIM-1", or "overworld"
                        : world.dimension().location().toString()) // handles custom dimensions
                .resolve("data")
                .resolve("projectatmosphere")
                .resolve(fileName);
    }

    public static Map<ResourceLocation, BlockPos> findBiomes(Level world, BlockPos center, int radiusBlocks) {
        Map<ResourceLocation, BlockPos> biomeSamples = new java.util.HashMap<>();
        Set<ResourceLocation> foundBiomes = new java.util.HashSet<>();
        findBiomes(world, center, radiusBlocks, foundBiomes, biomeSamples);
        return biomeSamples;
    }
}

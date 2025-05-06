package net.Gabou.projectatmosphere.util;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
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
     * Finds all biomes within a square area around the center position.
     * The area is defined by the radius in blocks.
     * The method uses a step of 16 blocks to sample the biomes.
     *
     * @param world        The world to search in.
     * @param center       The center position.
     * @param radiusBlocks The radius in blocks.
     * @param foundBiomes  A set to store found biome IDs.
     * @param biomeKeys    A set to store biome keys with their positions.
     */
    public static void findBiomes(Level world,
                                  BlockPos center,
                                  int radiusBlocks,
                                  Set<ResourceLocation> foundBiomes,
                                  Set<BiomeInstanceKey> biomeKeys) {

        int step = 16;
        for (int dx = -radiusBlocks; dx <= radiusBlocks; dx += step) {
            for (int dz = -radiusBlocks; dz <= radiusBlocks; dz += step) {
                BlockPos pos = center.offset(dx, 0, dz);
                ResourceLocation biomeId = world.getBiome(pos).unwrapKey().get().location();

                if (foundBiomes.add(biomeId)) {
                    biomeKeys.add(new BiomeInstanceKey(biomeId, pos));
                }
            }
        }

    }
    /**
     * Finds the nearest BiomeInstanceKey in the forecast map that matches the given biome type.
     *
     * @param biomeType   The biome type to match.
     * @param targetPos   The target position to find the nearest key.
     * @param forecastMap The map of BiomeInstanceKey to forecast data.
     * @return The nearest BiomeInstanceKey that matches the given biome type, or null if none found.
     */
    public static BiomeInstanceKey findNearestBiomeInstanceKey(
           BiomeInstanceKey type,
            Map<BiomeInstanceKey, ?> forecastMap
    ) {
        BiomeInstanceKey closestKey = null;
        double closestDistance = Double.MAX_VALUE;
        ResourceLocation biomeType = type.biomeType();
        BlockPos targetPos = type.samplePos();

        for (BiomeInstanceKey key : forecastMap.keySet()) {
            if (!key.biomeType().equals(biomeType)) continue;

            double dist = key.samplePos().distSqr(targetPos);
            if (dist < closestDistance) {
                closestDistance = dist;
                closestKey = key;
            }
        }

        return closestKey;
    }
    public static float[][] getRightForecastForBiome(BiomeInstanceKey biome, Map<BiomeInstanceKey, float[][]> forecastMap) {
        return forecastMap.get(findNearestBiomeInstanceKey(biome, forecastMap));
    }
    public static float[] getRightForecastForBiome1(
            BiomeInstanceKey biome,
            Map<BiomeInstanceKey, float[]> forecastMap
    ) {
        return forecastMap.get(findNearestBiomeInstanceKey(biome, forecastMap));
    }

    /** Finds all biomes within a square area around the center position.
     * The area is defined by the radius in blocks.
     * The method uses a step of 16 blocks to sample the biomes.
     *
     * @param world        The world to search in.
     * @param center       The center position.
     * @param radiusBlocks The radius in blocks.
     * @return A set of biome keys with their positions.
     */
    public static Set<BiomeInstanceKey> findBiomes(Level world, BlockPos center, int radiusBlocks) {
        Set<BiomeInstanceKey> biomeKeys = new HashSet<>();
        Set<ResourceLocation> foundBiomes = new HashSet<>();
        findBiomes(world, center, radiusBlocks, foundBiomes, biomeKeys);
        return biomeKeys;
    }

    /**
     * Serialize a BlockPos to a JsonObject.
     *
     * @param pos The BlockPos to serialize.
     * @return The serialized JsonObject.
     */
    public static JsonObject serializeBlockPos(BlockPos pos) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", pos.getX());
        obj.addProperty("y", pos.getY());
        obj.addProperty("z", pos.getZ());
        return obj;
    }

    /**
     * Deserialize a BlockPos from a JsonObject.
     *
     * @param obj The JsonObject to deserialize.
     * @return The deserialized BlockPos.
     */
    public static BlockPos deserializeBlockPos(JsonObject obj) {
        int x = obj.get("x").getAsInt();
        int y = obj.get("y").getAsInt();
        int z = obj.get("z").getAsInt();
        return new BlockPos(x, y, z);
    }
}

package net.Gabou.projectatmosphere.util;

import com.google.gson.JsonObject;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.wind.util.WindProfileManager;
import net.Gabou.projectatmosphere.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

import java.util.*;

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
                Optional<ResourceLocation> optionalKey = world.getBiome(pos).unwrapKey().map(ResourceKey::location);


                if (optionalKey.isPresent()) {
                    ResourceLocation biomeId = optionalKey.get();

                    if (foundBiomes.add(biomeId)) {
                        biomeKeys.add(new BiomeInstanceKey(biomeId, pos));
                    }
                }
            }
        }
    }
    public static BiomeInstanceKey findNearestBiomeInstanceKeyWithNoMap(
            ResourceLocation biomeType,
            BlockPos pos
    ) {
        // Step 1: Filter all biome samples by type
        List<BiomeInstanceKey> matchingType = AtmosphereManager.getBiomeSamples().stream()
                .filter(b -> b.biomeType().equals(biomeType))
                .toList();

        if (matchingType.isEmpty()) {
            System.err.println("[Atmosphere] No biome samples found for type: " + biomeType);
            return null;
        }

        // Step 2: From these, find the nearest within 600 blocks
        BiomeInstanceKey nearest = null;
        double minDistSqr = 360000.0; // 600 blocks squared

        for (BiomeInstanceKey b : matchingType) {
            double dist = b.samplePos().distSqr(pos);
            if (dist < minDistSqr) {
                minDistSqr = dist;
                nearest = b;
            }
        }

        if (nearest == null) {
            System.err.println("[Atmosphere] No nearby biome sample of type " + biomeType + " within 600 blocks of " + pos);
        }

        return nearest;
    }

//TODO check if it's returning the right biome
    /**
     * Finds the nearest biome instance key in the forecast map that matches the given type.
     *
     * @param type        The biome instance key to match.
     * @param forecastMap The map of biome instance keys to search in.
     * @return The nearest matching biome instance key, or null if none found.
     */
    public static BiomeInstanceKey findNearestBiomeInstanceKey(
            BiomeInstanceKey type,
            Map<BiomeInstanceKey, ?> forecastMap
    ) {
        // ✅ Direct hit: return immediately if key already exists
        if (forecastMap.containsKey(type)) {
            return type;
        }

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

        if (closestKey == null) {
            System.err.println("[Atmosphere] No matching biome found for " + biomeType + " at " + targetPos + forecastMap);
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
    public static WindVector[] getRightForecastForBiome4(
            BiomeInstanceKey biome,
            Map<BiomeInstanceKey, WindVector[]> forecastMap
    ) {
        return forecastMap.get(findNearestBiomeInstanceKey(biome, forecastMap));
    }

    public static float getRightForecastForBiome2(
            BiomeInstanceKey biome,
            Map<BiomeInstanceKey, Float> forecastMap
    ) {
        return forecastMap.get(findNearestBiomeInstanceKey(biome, forecastMap));
    }
    public static WindVector getRightForecastForBiome3(
            BiomeInstanceKey biome,
            Map<BiomeInstanceKey, WindVector> forecastMap
    ) {
        return forecastMap.get(findNearestBiomeInstanceKey(biome, forecastMap));
    }

    /**
     * Finds all biomes within a square area around the center position.
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
        obj.addProperty("x", String.valueOf(pos.getX()));
        obj.addProperty("y", String.valueOf(pos.getY()));
        obj.addProperty("z", String.valueOf(pos.getZ()));
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

    public static ResourceLocation getBiomeLocation(BlockPos pos,Level world) {
        return world.getBiome(pos).unwrapKey().get().location();
    }

    public static SimpleParticleType getSeasonalLeafParticle(ClientLevel level, BlockPos pos, RandomSource random) {
        Season season = getCurrentSeason(level, pos);

        List<SimpleParticleType> candidates = switch (season) {
            case AUTUMN -> List.of(
                    ModParticles.TRIANGLE_ORANGE.get(),
                    ModParticles.TRIANGLE_JAUNE.get(),
                    ModParticles.ROUND_ORANGE.get(),
                    ModParticles.ROUND_JAUNE.get(),
                    ModParticles.HEART_ORANGE.get(),
                    ModParticles.HEART_JAUNE.get()
            );
            case SPRING, SUMMER -> List.of(
                    ModParticles.TRIANGLE_VERT.get(),
                    ModParticles.ROUND_VERT.get(),
                    ModParticles.HEART_VERT.get()
            );
            default -> List.of(); // WINTER or null = no leaves
        };

        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    public static Season getCurrentSeason(ClientLevel level, BlockPos pos) {
        return SeasonHelper.getSeasonState(level).getSeason();
    }


}

package net.Gabou.projectatmosphere.util;

import com.google.gson.JsonObject;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.registry.ModParticles;
import net.minecraft.client.Minecraft;
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
        public static BiomeInstanceKey findNearestBiomeInstanceKeyWithNoMap(
            ResourceLocation biomeType,
            BlockPos pos
    ) {
        // Step 1: Filter all biome samples by type
        List<BiomeInstanceKey> matchingType = ForecastGenerator.getBiomeSamples().stream()
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
            minDistSqr = Double.MAX_VALUE;
            for (BiomeInstanceKey b : matchingType) {
                double dist = b.samplePos().distSqr(pos);
                if (dist < minDistSqr) {
                    minDistSqr = dist;
                    nearest = b;
                }
            }
        }

        return nearest;
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

    public static ResourceLocation getBiomeLocation(BlockPos pos, Level world) {
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

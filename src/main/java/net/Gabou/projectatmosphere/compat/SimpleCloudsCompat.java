package net.Gabou.projectatmosphere.compat;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.spawning.SpawnInfo;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector2f;

import java.util.Optional;

public class SimpleCloudsCompat {

    public static void spawnCloudInBiome(String cloudId, BiomeInstanceKey key, ServerLevel level,WindVector windVector) {

        ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(level);
        CloudGenerator generator = cloudManager.getCloudGenerator();
        CloudSpawningConfig config = generator.getSpawnConfig().get();

        if(!ClientSyncLock.isReady())
        {
            System.out.println("[Atmosphere] SimpleClouds is not ready yet, cannot spawn cloud: " + cloudId);
            return;
        }

        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID,cloudId);
        CloudSpawningConfig.Info info = config.getWeightInfo(rl);
        if (info == null) {
            System.out.println("[Atmosphere] Unknown cloud type: " + cloudId);
            return;
        }
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Spawning cloud: " + cloudId);
        SpawnRegion targetRegion = generator.getSpawnRegions().iterator().next();
        float x = targetRegion.x() + 0.5F;
        float z = targetRegion.z() + 0.5F;

        float px = x; // simulated player
        float pz = z;

        Optional<CloudRegion> region = generator.spawnCloud(() -> info, 1, config.getMaxRegions(), level,
                (spawnInfo, playerX, playerZ, realX, realZ, rand, grow) ->
                        spawnCloudInBiome(
                                spawnInfo,
                                key,                              // your existing BiomeInstanceKey
                                level,
                                rand,
                                windVector,
                                generator
                        )
        );




        region.ifPresentOrElse(r ->
                        System.out.println("[Atmosphere] Spawned " + cloudId + " at " + x + ", " + z + " in " + key.biomeType()),
                () -> System.out.println("[Atmosphere] Failed to spawn " + cloudId + " in " + key.biomeType())
        );
    }
    public static Optional<CloudRegion> spawnCloudInBiome(
            SpawnInfo info,
            BiomeInstanceKey biomeKey,
            ServerLevel level,
            RandomSource random,
            WindVector wind,
            CloudGenerator generator
    ) {
        float x = biomeKey.samplePos().getX();
        float z = biomeKey.samplePos().getZ();
        float windAngleRad =wind.angleRadians();

        // Avoid overlapping with existing clouds (same logic)
        for (CloudRegion region : generator.getClouds()) {
            float dist = Vector2f.distance(x, z, region.getWorldX(), region.getWorldZ()) - region.getWorldRadius();
            if (dist <= 500.0F) {
                return Optional.empty();
            }
        }
        float dx = (float) Math.sin(windAngleRad);
        float dz = (float) Math.cos(windAngleRad);
        Vec2 direction = new Vec2(dx, dz).normalized();

// Optional: rotation for animation
        float rotation = windAngleRad + (float) Math.PI;


        // Optionally override cloud attributes based on biome here
        float radius = info.determineRadius(random);
        float speed = info.determineSpeed(random) + wind.speed() * 0.1F; // Add wind speed influence
        float acceleration = 0.01F;
        int duration = info.determineExistTicks(random);
        int growTicks = info.determineGrowTicks(random);
        float stretch = info.determineStretchFactor(random);

        // Return new region
        CloudRegion region = new CloudRegion(
                info.cloudType(),
                direction,
                speed,
                acceleration,
                x / 8.0F,
                z / 8.0F,
                radius / 8.0F,
                rotation,
                stretch,
                duration,
                growTicks,
                info.orderWeight()
        );

        return Optional.of(region);
    }



}

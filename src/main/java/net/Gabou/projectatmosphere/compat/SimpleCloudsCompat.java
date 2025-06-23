package net.Gabou.projectatmosphere.compat;

import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

public class SimpleCloudsCompat {

    public static void spawnCloudInBiome(String cloudId, BiomeInstanceKey key, ServerLevel level) {
        ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(level);
        CloudGenerator generator = cloudManager.getCloudGenerator();
        CloudSpawningConfig config = generator.getSpawnConfig().get();

        ResourceLocation rl = new ResourceLocation(cloudId);
        CloudSpawningConfig.Info info = config.getWeightInfo(rl);
        if (info == null) {
            System.out.println("[Atmosphere] Unknown cloud type: " + cloudId);
            return;
        }

        BlockPos samplePos = key.samplePos();
        float x = samplePos.getX() + 0.5F;
        float z = samplePos.getZ() + 0.5F;

        float px = x + 2000; // Simulated "player" location for wind direction
        float pz = z + 2000;

        Optional<CloudRegion> region = generator.spawnCloud(() -> info, 1, config.getMaxRegions(), level,
                (spawnInfo, playerX, playerZ, realX, realZ, rand, grow) ->
                        generator.createRegion(spawnInfo, px, pz, x, z, rand, grow)
        );

        region.ifPresentOrElse(r ->
                        System.out.println("[Atmosphere] Spawned " + cloudId + " at " + x + ", " + z + " in " + key.biomeType()),
                () -> System.out.println("[Atmosphere] Failed to spawn " + cloudId + " in " + key.biomeType())
        );
    }


}

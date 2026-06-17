package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class CloudGroupSpawner {
    private CloudGroupSpawner() {
    }

    public static @Nullable CloudRegionState spawnRequestedCloud(ServerLevel level, BlockPos pos, String cloudTypeId) {
        if (level == null || pos == null || !level.dimension().equals(Level.OVERWORLD)) {
            return null;
        }

        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        String resolvedTypeId = definition.getId();
        float spawnHeight = AtmoCommonConfig.NATIVE_CLOUD_SPAWN_HEIGHT.get();
        RandomSource random = level.getRandom();
        CloudMorphologyGenerators.SpawnPlan morphology = CloudMorphologyGenerators.createSpawnPlan(definition, random);
        Vec3 center = new Vec3(pos.getX(), spawnHeight, pos.getZ());
        RegionInstanceKey sourceRegionKey = RegionInstanceKey.from(pos);
        CloudRegionState state = CloudRegionManager.getInstance().createCloudRegion(
                level,
                center,
                morphology.radius(),
                spawnHeight - morphology.baseDrop(),
                spawnHeight + morphology.topRise(),
                morphology.density(),
                morphology.coverage(),
                morphology.edgeSoftness(),
                sourceRegionKey,
                resolvedTypeId
        );

        CloudClusterState primary = state.getClusters().stream().findFirst().orElse(null);
        if (primary != null) {
            CloudMorphologyGenerators.tuneSpawnedCluster(primary, definition, morphology, 1.0F, 0, random);
        }

        for (int i = 1; i < morphology.clusterCount(); i++) {
            Vec3 clusterCenter = CloudMorphologyGenerators.createClusterCenter(center, morphology, i, random);
            float scale = 0.72F + random.nextFloat() * 0.42F;
            CloudClusterState cluster = new CloudClusterState(
                    UUID.randomUUID(),
                    level.dimension(),
                    clusterCenter,
                    Math.max(8.0F, morphology.radius() * scale),
                    spawnHeight - morphology.baseDrop() * (0.84F + random.nextFloat() * 0.20F),
                    spawnHeight + morphology.topRise() * (0.82F + random.nextFloat() * 0.34F),
                    morphology.density() * (0.86F + random.nextFloat() * 0.22F),
                    morphology.coverage() * (0.84F + random.nextFloat() * 0.24F),
                    morphology.edgeSoftness()
            );
            cluster.setCloudTypeId(resolvedTypeId);
            cluster.setPreviousCloudTypeId(resolvedTypeId);
            cluster.setMorphologyFamily(definition.getMorphologyFamily());
            CloudMorphologyGenerators.tuneSpawnedCluster(cluster, definition, morphology, scale, i, random);
            state.addCluster(cluster);
        }

        CloudRegionStateStore.markDirty(level);
        return state;
    }
}

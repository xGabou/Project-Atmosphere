package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.platform.config.AtmosphereConfig;
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

    /**
     * T237. Re-lays the live storms out with a storm template version (0 the
     * T134/T127 layout, 1 the T237 layout) for the same-storm capture A/B.
     * Server thread only.
     */
    public static String relayoutStorms(ServerLevel level, int version) {
        return CloudMorphologyGenerators.relayoutStormRegions(level, version);
    }

    /**
     * High-level ice cloud (cirrus) forms near the anvil-top level of the
     * storms, far above the low and middle families that share the native
     * spawn height.
     */
    static float altitudeOffset(CloudMorphologyFamily family) {
        return family == CloudMorphologyFamily.FILAMENT ? CIRRUS_ALTITUDE_OFFSET : 0.0F;
    }

    static final float CIRRUS_ALTITUDE_OFFSET = 480.0F;

    public static @Nullable CloudRegionState spawnRequestedCloud(ServerLevel level, BlockPos pos, String cloudTypeId) {
        if (level == null || pos == null || !level.dimension().equals(Level.OVERWORLD)) {
            return null;
        }

        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        String resolvedTypeId = definition.getId();
        float spawnHeight = AtmosphereConfig.clouds().nativeCloudSpawnHeight()
                + altitudeOffset(definition.getMorphologyFamily());
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
            primary.setMorphologyMembership(state.getRegionId(), 0, morphology.clusterCount());
            CloudMorphologyGenerators.tuneSpawnedCluster(primary, definition, morphology, 1.0F, 0, random);
        }

        // T242: a continuous deck (stratus, nimbostratus) is one layer - its
        // members share base, top, density and coverage, so the deck reads as
        // one surface instead of a cluster of lobes. The draws are still made
        // so every other family keeps its random sequence.
        boolean deck = definition.getMorphologyFamily() == CloudMorphologyFamily.SHEET;
        for (int i = 1; i < morphology.clusterCount(); i++) {
            Vec3 clusterCenter = CloudMorphologyGenerators.createClusterCenter(center, morphology, i, random);
            float scale = 0.72F + random.nextFloat() * 0.42F;
            float baseFactor = 0.84F + random.nextFloat() * 0.20F;
            float topFactor = 0.82F + random.nextFloat() * 0.34F;
            float densityFactor = 0.86F + random.nextFloat() * 0.22F;
            float coverageFactor = 0.84F + random.nextFloat() * 0.24F;
            if (deck) {
                baseFactor = 1.0F;
                topFactor = 1.0F;
                densityFactor = 1.0F;
                coverageFactor = 1.0F;
                scale = 1.0F;
            }
            CloudClusterState cluster = new CloudClusterState(
                    UUID.randomUUID(),
                    level.dimension(),
                    clusterCenter,
                    Math.max(8.0F, morphology.radius()),
                    spawnHeight - morphology.baseDrop() * baseFactor,
                    spawnHeight + morphology.topRise() * topFactor,
                    morphology.density() * densityFactor,
                    morphology.coverage() * coverageFactor,
                    morphology.edgeSoftness()
            );
            cluster.setCloudTypeId(resolvedTypeId);
            cluster.setPreviousCloudTypeId(resolvedTypeId);
            cluster.setMorphologyFamily(definition.getMorphologyFamily());
            cluster.setMorphologyMembership(state.getRegionId(), i, morphology.clusterCount());
            CloudMorphologyGenerators.tuneSpawnedCluster(cluster, definition, morphology, scale, i, random);
            state.addCluster(cluster);
        }

        CloudRegionStateStore.markDirty(level);
        return state;
    }
}

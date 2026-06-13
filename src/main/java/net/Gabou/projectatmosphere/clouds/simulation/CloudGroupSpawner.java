package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.clouds.type.CloudVisualProfile;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
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
        Morphology morphology = resolveMorphology(definition, random);
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
            tuneCluster(primary, definition, morphology, 1.0F, random);
        }

        for (int i = 1; i < morphology.clusterCount(); i++) {
            float angle = (float) (random.nextFloat() * Math.PI * 2.0D);
            float distance = morphology.groupRadius() * (0.18F + random.nextFloat() * 0.72F);
            Vec3 clusterCenter = center.add(Math.cos(angle) * distance, random.nextFloat() * 10.0F - 5.0F, Math.sin(angle) * distance);
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
            tuneCluster(cluster, definition, morphology, scale, random);
            state.addCluster(cluster);
        }

        CloudRegionStateStore.markDirty(level);
        return state;
    }

    private static Morphology resolveMorphology(CloudTypeDefinition definition, RandomSource random) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        CloudFamily family = definition.getFamily();
        String id = definition.getId().toLowerCase(Locale.ROOT);

        int minClusters = 1;
        int maxClusters = 2;
        float radiusScale = 1.0F;
        float groupScale = 1.15F;
        if (id.contains("humilis")) {
            maxClusters = 3;
            radiusScale = 0.86F;
        } else if (id.contains("mediocris")) {
            minClusters = 2;
            maxClusters = 4;
            radiusScale = 1.08F;
            groupScale = 1.55F;
        } else if (id.contains("congestus")) {
            minClusters = 3;
            maxClusters = 6;
            radiusScale = 1.35F;
            groupScale = 2.05F;
        } else if (family == CloudFamily.CUMULONIMBUS) {
            minClusters = 5;
            maxClusters = 9;
            radiusScale = 1.70F;
            groupScale = 2.55F;
        } else if (family == CloudFamily.NIMBOSTRATUS || family == CloudFamily.STRATOCUMULUS) {
            minClusters = 4;
            maxClusters = 8;
            radiusScale = 1.45F;
            groupScale = 2.40F;
        }

        int clusterCount = minClusters + random.nextInt(Math.max(1, maxClusters - minClusters + 1));
        float radius = Math.max(24.0F, shape.getBaseRadius() * radiusScale * (0.90F + random.nextFloat() * 0.24F));
        float density = Mth.clamp(0.42F + visual.getDensityMultiplier() * 0.26F + visual.getPrecipitationCoreStrength() * 0.16F, 0.16F, 0.96F);
        float coverage = Mth.clamp(0.48F + visual.getCoverageMultiplier() * 0.28F + shape.getBaseFlattening() * 0.10F, 0.20F, 0.98F);
        float edgeSoftness = Mth.clamp(0.18F + visual.getTopSoftness() * 0.42F + shape.getEdgeRaggedness() * 0.12F, 0.08F, 0.82F);
        return new Morphology(
                clusterCount,
                radius,
                radius * groupScale,
                Math.max(8.0F, shape.getBaseOffset()),
                Math.max(10.0F, shape.getTopOffset()),
                density,
                coverage,
                edgeSoftness
        );
    }

    private static void tuneCluster(CloudClusterState cluster, CloudTypeDefinition definition, Morphology morphology, float scale, RandomSource random) {
        CloudRegionTypeGeometry.apply(cluster, definition.getId());
        cluster.setRadius(Math.max(8.0F, cluster.getRadius() * scale * (0.92F + random.nextFloat() * 0.16F)));
        cluster.setDensity(Mth.clamp(morphology.density() * (0.92F + random.nextFloat() * 0.16F), 0.0F, 1.0F));
        cluster.setCoverage(Mth.clamp(morphology.coverage() * (0.90F + random.nextFloat() * 0.18F), 0.0F, 1.0F));
        cluster.setEdgeSoftness(Mth.clamp(morphology.edgeSoftness() * (0.90F + random.nextFloat() * 0.22F), 0.02F, 0.95F));
    }

    private record Morphology(
            int clusterCount,
            float radius,
            float groupRadius,
            float baseDrop,
            float topRise,
            float density,
            float coverage,
            float edgeSoftness
    ) {
    }
}

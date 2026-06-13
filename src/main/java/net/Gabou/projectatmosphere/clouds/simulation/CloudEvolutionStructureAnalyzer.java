package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Locale;

final class CloudEvolutionStructureAnalyzer {
    private static final float NEIGHBOR_SEARCH_RADIUS = 360.0F;

    private CloudEvolutionStructureAnalyzer() {
    }

    static @NotNull CloudStructuralInputs analyze(
            @NotNull CloudRegionState owner,
            @NotNull CloudClusterState cluster,
            @NotNull Collection<CloudRegionState> activeRegions
    ) {
        Vec3 center = cluster.getCenter();
        int nearbyClusterCount = 1;
        float mergedMass = massOf(cluster);
        float weightedCoverage = cluster.getCoverage() * massOf(cluster);
        float neighborDensity = 0.0F;
        float neighborDensityWeight = 0.0F;
        float groupRadius = cluster.getRadius();
        boolean canMerge = false;

        for (CloudRegionState region : activeRegions) {
            if (region == null || !region.isActive()) {
                continue;
            }

            for (CloudClusterState other : region.getClusters()) {
                if (other == null || !other.isActive() || other == cluster) {
                    continue;
                }

                double distance = horizontalDistance(center, other.getCenter());
                float reach = cluster.getRadius() + other.getRadius() + NEIGHBOR_SEARCH_RADIUS;
                if (distance > reach) {
                    continue;
                }

                float proximity = 1.0F - Mth.clamp((float) (distance / Math.max(1.0F, reach)), 0.0F, 1.0F);
                float otherMass = massOf(other) * Math.max(0.12F, proximity);
                nearbyClusterCount++;
                mergedMass += otherMass;
                weightedCoverage += other.getCoverage() * otherMass;
                neighborDensity += other.getDensity() * proximity;
                neighborDensityWeight += proximity;
                groupRadius = Math.max(groupRadius, (float) distance + other.getRadius());
                canMerge |= distance <= (cluster.getRadius() + other.getRadius()) * 0.72F;
            }
        }

        float groupCoverage = mergedMass <= 0.001F ? cluster.getCoverage() : Mth.clamp(weightedCoverage / mergedMass, 0.0F, 1.0F);
        float neighborClusterDensity = neighborDensityWeight <= 0.001F ? 0.0F : Mth.clamp(neighborDensity / neighborDensityWeight, 0.0F, 1.0F);
        float cloudSize = cluster.getRadius();
        float cloudVolume = cloudSize * Math.max(1.0F, cluster.getTopY() - cluster.getBaseY()) * Mth.clamp(cluster.getCoverage(), 0.0F, 1.0F) / 4096.0F;

        return new CloudStructuralInputs(
                nearbyClusterCount,
                mergedMass,
                groupRadius,
                groupCoverage,
                neighborClusterDensity,
                cloudSize,
                cloudVolume,
                canMerge || owner.getStrongestInteractionStrength() > 0.20F
        );
    }

    private static float massOf(@NotNull CloudClusterState cluster) {
        float verticalSpan = Math.max(1.0F, cluster.getTopY() - cluster.getBaseY());
        return Math.max(0.10F, cluster.getRadius() * cluster.getRadius() * verticalSpan * cluster.getCoverage() * cluster.getDensity() / 262144.0F);
    }

    private static double horizontalDistance(@NotNull Vec3 first, @NotNull Vec3 second) {
        double dx = first.x() - second.x();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    record CloudStructuralInputs(
            int nearbyClusterCount,
            float mergedMass,
            float groupRadius,
            float groupCoverage,
            float neighborClusterDensity,
            float cloudSize,
            float cloudVolume,
            boolean canMerge
    ) {
        String describe() {
            return String.format(
                    Locale.ROOT,
                    "clusterCountNearby=%d mergedMass=%.3f groupRadius=%.1f groupCoverage=%.3f neighborClusterDensity=%.3f cloudSize=%.1f cloudVolume=%.3f canMerge=%s",
                    nearbyClusterCount,
                    mergedMass,
                    groupRadius,
                    groupCoverage,
                    neighborClusterDensity,
                    cloudSize,
                    cloudVolume,
                    canMerge
            );
        }
    }
}

package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves cloud-region collisions and cluster blending.
 */
final class CloudRegionMergeController {

    private static final float SMOOTHING = 0.15F;
    private static final float STRONG_REGION_MERGE_THRESHOLD = 0.72F;
    private static final float MODERATE_REGION_INTERACTION_THRESHOLD = 0.35F;
    private static final float STRONG_CLUSTER_MERGE_THRESHOLD = 0.62F;

    boolean tick(@NotNull ServerLevel level, @NotNull Collection<CloudRegionState> activeRegions) {
        if (activeRegions.isEmpty()) {
            return false;
        }

        List<CloudRegionState> regions = new ArrayList<>(activeRegions);
        Map<UUID, Float> clusterPressureById = new HashMap<>();
        Set<UUID> removedRegions = new HashSet<>();
        boolean changed = false;

        for (CloudRegionState region : regions) {
            if (region != null) {
                region.clearInteractions();
            }
        }

        for (CloudRegionState region : regions) {
            if (region == null || removedRegions.contains(region.getRegionId()) || region.isEmpty()) {
                continue;
            }
            changed |= mergeWithinRegion(region, clusterPressureById);
        }

        for (int i = 0; i < regions.size(); i++) {
            CloudRegionState first = regions.get(i);
            if (first == null || removedRegions.contains(first.getRegionId()) || first.isEmpty()) {
                continue;
            }

            for (int j = i + 1; j < regions.size(); j++) {
                CloudRegionState second = regions.get(j);
                if (second == null || removedRegions.contains(second.getRegionId()) || second.isEmpty()) {
                    continue;
                }

                float overlap = computeRegionOverlap(first, second);
                if (overlap <= 0.0F) {
                    continue;
                }

                if (overlap >= STRONG_REGION_MERGE_THRESHOLD) {
                    CloudRegionState dominant = selectDominantRegion(first, second, overlap);
                    CloudRegionState absorbed = dominant == first ? second : first;
                    changed |= mergeRegions(level, dominant, absorbed, removedRegions);
                    continue;
                }

                if (overlap >= MODERATE_REGION_INTERACTION_THRESHOLD) {
                    first.linkInteraction(second.getRegionId(), overlap);
                    second.linkInteraction(first.getRegionId(), overlap);
                    changed |= mergeBestClusterPairAcrossRegions(level, first, second, overlap, clusterPressureById, removedRegions);
                }
            }
        }

        for (CloudRegionState region : regions) {
            if (region == null || removedRegions.contains(region.getRegionId()) || region.isEmpty()) {
                continue;
            }

            float regionInfluence = region.getStrongestInteractionStrength();
            for (CloudClusterState cluster : region.getClusters()) {
                if (cluster == null || !cluster.isActive()) {
                    continue;
                }

                float clusterInfluence = Math.max(regionInfluence, clusterPressureById.getOrDefault(cluster.getClusterId(), 0.0F));
                float blended = Mth.clamp(cluster.getMergePressure() * (1.0F - SMOOTHING) + clusterInfluence * SMOOTHING, 0.0F, 1.0F);
                if (Math.abs(blended - cluster.getMergePressure()) > 0.0001F) {
                    cluster.setMergePressure(blended);
                    changed = true;
                }
            }
        }

        if (changed) {
            CloudRegionStateStore.markDirty(level);
        }

        return changed;
    }

    private boolean mergeWithinRegion(
            @NotNull CloudRegionState region,
            @NotNull Map<UUID, Float> clusterPressureById
    ) {
        List<CloudClusterState> clusters = new ArrayList<>(region.getClusters());
        Set<UUID> removedClusters = new HashSet<>();
        boolean changed = false;

        for (int i = 0; i < clusters.size(); i++) {
            CloudClusterState first = clusters.get(i);
            if (first == null || removedClusters.contains(first.getClusterId()) || !region.getClusters().contains(first)) {
                continue;
            }

            for (int j = i + 1; j < clusters.size(); j++) {
                CloudClusterState second = clusters.get(j);
                if (second == null || removedClusters.contains(second.getClusterId()) || !region.getClusters().contains(second)) {
                    continue;
                }

                // A morphology group is intentionally built from overlapping
                // persistent lobes. Treating those siblings as colliding
                // simulation cells collapsed every cumulus tower into one tall
                // aggregate within a few ticks. Independent cloud formations
                // retain the existing interaction/absorption behavior.
                if (first.isMorphologySibling(second)) {
                    continue;
                }

                float overlap = computeClusterOverlap(first, second);
                if (overlap <= 0.0F) {
                    continue;
                }

                clusterPressureById.merge(first.getClusterId(), overlap, Math::max);
                clusterPressureById.merge(second.getClusterId(), overlap, Math::max);

                if (overlap < STRONG_CLUSTER_MERGE_THRESHOLD) {
                    continue;
                }

                CloudClusterState dominant = selectDominantCluster(first, second, overlap);
                CloudClusterState absorbed = dominant == first ? second : first;
                dominant.absorb(absorbed);
                absorbed.setActive(false);
                region.removeCluster(absorbed);
                removedClusters.add(absorbed.getClusterId());
                changed = true;
            }
        }

        return changed;
    }

    private boolean mergeBestClusterPairAcrossRegions(
            @NotNull ServerLevel level,
            @NotNull CloudRegionState firstRegion,
            @NotNull CloudRegionState secondRegion,
            float regionOverlap,
            @NotNull Map<UUID, Float> clusterPressureById,
            @NotNull Set<UUID> removedRegions
    ) {
        CloudClusterState firstCluster = null;
        CloudClusterState secondCluster = null;
        float bestScore = 0.0F;

        for (CloudClusterState first : firstRegion.getClusters()) {
            if (first == null || !first.isActive()) {
                continue;
            }

            for (CloudClusterState second : secondRegion.getClusters()) {
                if (second == null || !second.isActive()) {
                    continue;
                }

                float overlap = computeClusterOverlap(first, second);
                clusterPressureById.merge(first.getClusterId(), overlap, Math::max);
                clusterPressureById.merge(second.getClusterId(), overlap, Math::max);

                if (overlap > bestScore || (overlap == bestScore && first.getClusterId().compareTo(firstCluster == null ? first.getClusterId() : firstCluster.getClusterId()) < 0)) {
                    bestScore = overlap;
                    firstCluster = first;
                    secondCluster = second;
                }
            }
        }

        if (firstCluster == null || secondCluster == null || bestScore < STRONG_CLUSTER_MERGE_THRESHOLD) {
            return false;
        }

        CloudClusterState dominantCluster = selectDominantCluster(firstCluster, secondCluster, bestScore);
        CloudClusterState absorbedCluster = dominantCluster == firstCluster ? secondCluster : firstCluster;
        CloudRegionState absorbedRegion = firstRegion.getClusters().contains(absorbedCluster) ? firstRegion : secondRegion;

        dominantCluster.absorb(absorbedCluster);
        absorbedCluster.setActive(false);
        absorbedRegion.removeCluster(absorbedCluster);

        if (absorbedRegion.isEmpty()) {
            absorbedRegion.setActive(false);
            CloudRegionStateStore.remove(level, absorbedRegion.getRegionId());
            removedRegions.add(absorbedRegion.getRegionId());
        }

        return true;
    }

    private boolean mergeRegions(
            @NotNull ServerLevel level,
            @NotNull CloudRegionState dominant,
            @NotNull CloudRegionState absorbed,
            @NotNull Set<UUID> removedRegions
    ) {
        if (dominant == absorbed) {
            return false;
        }

        dominant.mergeRegionFrom(absorbed);
        absorbed.clearClusters();
        absorbed.clearInteractions();
        absorbed.setActive(false);
        CloudRegionStateStore.remove(level, absorbed.getRegionId());
        removedRegions.add(absorbed.getRegionId());
        return true;
    }

    private float computeRegionOverlap(@NotNull CloudRegionState first, @NotNull CloudRegionState second) {
        Vec3 firstCenter = first.getCenter();
        Vec3 secondCenter = second.getCenter();
        double dx = firstCenter.x() - secondCenter.x();
        double dz = firstCenter.z() - secondCenter.z();
        double horizontalDistance = Math.sqrt((dx * dx) + (dz * dz));
        float horizontalReach = first.getRadius() + second.getRadius();
        if (horizontalReach <= 0.0F) {
            return 0.0F;
        }

        float horizontalOverlap = 1.0F - Mth.clamp((float) (horizontalDistance / horizontalReach), 0.0F, 1.0F);
        if (horizontalOverlap <= 0.0F) {
            return 0.0F;
        }

        float firstBase = first.getBaseY();
        float firstTop = first.getTopY();
        float secondBase = second.getBaseY();
        float secondTop = second.getTopY();
        float verticalOverlapDistance = Math.max(0.0F, Math.min(firstTop, secondTop) - Math.max(firstBase, secondBase));
        float verticalSpan = Math.max(1.0F, Math.min(firstTop - firstBase, secondTop - secondBase));
        float verticalOverlap = Mth.clamp(verticalOverlapDistance / verticalSpan, 0.0F, 1.0F);

        return Mth.clamp((horizontalOverlap * 0.65F) + (verticalOverlap * 0.35F), 0.0F, 1.0F);
    }

    private float computeClusterOverlap(@NotNull CloudClusterState first, @NotNull CloudClusterState second) {
        Vec3 firstCenter = first.getCenter();
        Vec3 secondCenter = second.getCenter();
        double dx = firstCenter.x() - secondCenter.x();
        double dz = firstCenter.z() - secondCenter.z();
        double horizontalDistance = Math.sqrt((dx * dx) + (dz * dz));
        float horizontalReach = first.getRadius() + second.getRadius();
        if (horizontalReach <= 0.0F) {
            return 0.0F;
        }

        float horizontalOverlap = 1.0F - Mth.clamp((float) (horizontalDistance / horizontalReach), 0.0F, 1.0F);
        if (horizontalOverlap <= 0.0F) {
            return 0.0F;
        }

        float firstBase = first.getBaseY();
        float firstTop = first.getTopY();
        float secondBase = second.getBaseY();
        float secondTop = second.getTopY();
        float verticalOverlapDistance = Math.max(0.0F, Math.min(firstTop, secondTop) - Math.max(firstBase, secondBase));
        float verticalSpan = Math.max(1.0F, Math.min(firstTop - firstBase, secondTop - secondBase));
        float verticalOverlap = Mth.clamp(verticalOverlapDistance / verticalSpan, 0.0F, 1.0F);

        float familyBias = first.getCloudFamily() == second.getCloudFamily() ? 0.92F : 1.0F;
        return Mth.clamp(((horizontalOverlap * 0.65F) + (verticalOverlap * 0.35F)) * familyBias, 0.0F, 1.0F);
    }

    private CloudRegionState selectDominantRegion(
            @NotNull CloudRegionState first,
            @NotNull CloudRegionState second,
            float overlap
    ) {
        float firstScore = scoreRegion(first, overlap);
        float secondScore = scoreRegion(second, overlap);
        if (firstScore > secondScore) {
            return first;
        }
        if (secondScore > firstScore) {
            return second;
        }
        return first.getRegionId().compareTo(second.getRegionId()) <= 0 ? first : second;
    }

    private float scoreRegion(@NotNull CloudRegionState region, float overlap) {
        float score = 0.0F;
        for (CloudClusterState cluster : region.getClusters()) {
            if (cluster != null && cluster.isActive()) {
                score += cluster.getFootprint();
            }
        }
        return score * Math.max(0.01F, overlap);
    }

    private CloudClusterState selectDominantCluster(
            @NotNull CloudClusterState first,
            @NotNull CloudClusterState second,
            float overlap
    ) {
        float firstScore = first.getFootprint() * Math.max(0.01F, overlap);
        float secondScore = second.getFootprint() * Math.max(0.01F, overlap);
        if (firstScore > secondScore) {
            return first;
        }
        if (secondScore > firstScore) {
            return second;
        }
        return first.getClusterId().compareTo(second.getClusterId()) <= 0 ? first : second;
    }
}

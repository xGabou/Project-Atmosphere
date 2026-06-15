package net.Gabou.projectatmosphere.clouds.visual;

import net.Gabou.projectatmosphere.clouds.network.CloudRegionPacketDispatcher;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only cloud visual metadata accessor for future render, shader,
 * shadow, and Distant Horizons systems.
 */
public final class CloudVisualStateManager {
    private static final double DEFAULT_NEARBY_RADIUS = 1600.0D;
    private static final float DISTANT_IMPORTANCE_THRESHOLD = 0.28F;
    private static final float SHADOW_CANDIDATE_THRESHOLD = 0.18F;
    private static final float STORM_CANDIDATE_THRESHOLD = 0.25F;

    private CloudVisualStateManager() {
    }

    public static @NotNull List<CloudVisualState> getActiveCloudVisualStates(@NotNull Level level) {
        return toVisualStates(getRenderData(level));
    }

    public static @NotNull List<CloudVisualState> getNearbyCloudVisualStates(@NotNull Level level, @NotNull BlockPos center) {
        return getNearbyCloudVisualStates(level, Vec3.atCenterOf(center), DEFAULT_NEARBY_RADIUS);
    }

    public static @NotNull List<CloudVisualState> getNearbyCloudVisualStates(@NotNull Level level, @NotNull Vec3 center, double radius) {
        double maxDistanceSq = Math.max(0.0D, radius) * Math.max(0.0D, radius);
        List<CloudVisualState> states = new ArrayList<>();
        for (CloudVisualState state : getActiveCloudVisualStates(level)) {
            if (horizontalDistanceSq(center, state.position()) <= maxDistanceSq) {
                states.add(state);
            }
        }
        states.sort(Comparator.comparingDouble(state -> horizontalDistanceSq(center, state.position())));
        return List.copyOf(states);
    }

    public static @NotNull List<CloudVisualState> getImportantDistantCloudVisualStates(@NotNull Level level, @NotNull Vec3 center, double nearRadius) {
        double nearRadiusSq = Math.max(0.0D, nearRadius) * Math.max(0.0D, nearRadius);
        List<CloudVisualState> states = new ArrayList<>();
        for (CloudVisualState state : getActiveCloudVisualStates(level)) {
            if (horizontalDistanceSq(center, state.position()) <= nearRadiusSq) {
                continue;
            }
            if (state.visibilityImportance() >= DISTANT_IMPORTANCE_THRESHOLD) {
                states.add(state);
            }
        }
        states.sort(Comparator.comparing(CloudVisualState::visibilityImportance).reversed());
        return List.copyOf(states);
    }

    public static @NotNull List<CloudVisualState> getCloudShadowCandidates(@NotNull Level level) {
        List<CloudVisualState> states = new ArrayList<>();
        for (CloudVisualState state : getActiveCloudVisualStates(level)) {
            if (state.shadowPotential() >= SHADOW_CANDIDATE_THRESHOLD && state.isShadowCandidate()) {
                states.add(state);
            }
        }
        states.sort(Comparator.comparing(CloudVisualState::shadowPotential).reversed());
        return List.copyOf(states);
    }

    /**
     * Returns storm and heavy-rain clouds that should influence fallback world
     * darkening. Fair-weather puffs and weak fragments are excluded.
     */
    public static @NotNull List<CloudVisualState> getFallbackDarkeningCandidates(@NotNull Level level) {
        List<CloudVisualState> states = new ArrayList<>();
        for (CloudVisualState state : getActiveCloudVisualStates(level)) {
            if (CloudLightingEvaluation.isFallbackDarkeningCandidate(state)) {
                states.add(state);
            }
        }
        states.sort(Comparator.comparing(CloudVisualState::shadowPotential).reversed());
        return List.copyOf(states);
    }

    public static @NotNull List<CloudVisualState> getStormVisualCandidates(@NotNull Level level) {
        List<CloudVisualState> states = new ArrayList<>();
        for (CloudVisualState state : getActiveCloudVisualStates(level)) {
            if (state.stormStrength() >= STORM_CANDIDATE_THRESHOLD || state.isStormCandidate()) {
                states.add(state);
            }
        }
        states.sort(Comparator.comparing(CloudVisualState::stormStrength).reversed());
        return List.copyOf(states);
    }

    public static @NotNull List<CloudShaderMetadata> getShaderMetadata(@NotNull Level level) {
        List<CloudShaderMetadata> metadata = new ArrayList<>();
        for (CloudVisualState state : getActiveCloudVisualStates(level)) {
            metadata.add(CloudShaderMetadata.from(state));
        }
        return List.copyOf(metadata);
    }

    public static @NotNull List<CloudDistantHorizonMetadata> getDistantHorizonMetadata(@NotNull Level level, @NotNull Vec3 center, double nearRadius) {
        List<CloudDistantHorizonMetadata> metadata = new ArrayList<>();
        for (CloudVisualState state : getImportantDistantCloudVisualStates(level, center, nearRadius)) {
            metadata.add(CloudDistantHorizonMetadata.from(state));
        }
        metadata.sort(Comparator.comparing(CloudDistantHorizonMetadata::lodPriority).reversed());
        return List.copyOf(metadata);
    }

    public static @NotNull List<CloudVisualState> toVisualStates(Collection<CloudRegionRenderData> renderData) {
        if (renderData == null || renderData.isEmpty()) {
            return List.of();
        }
        List<CloudVisualState> states = new ArrayList<>(renderData.size());
        for (CloudRegionRenderData data : renderData) {
            if (data == null || !data.isActive()) {
                continue;
            }
            CloudVisualState state = CloudVisualStateFactory.fromRenderData(data);
            if (state != null) {
                states.add(state);
            }
        }
        states.sort(Comparator.comparing(CloudVisualState::visibilityImportance).reversed());
        return List.copyOf(states);
    }

    private static Collection<CloudRegionRenderData> getRenderData(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return CloudRegionStateStore.createRenderDataForActiveRegions(serverLevel);
        }
        return CloudRegionPacketDispatcher.getClientRegions();
    }

    private static double horizontalDistanceSq(Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }
}

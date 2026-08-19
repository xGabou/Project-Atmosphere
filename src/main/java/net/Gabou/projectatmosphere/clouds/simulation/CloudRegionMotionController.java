package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.platform.config.AtmosphereConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Gère le mouvement des régions de nuage backend.
 * Cette classe ne crée pas de nuage, ne sauvegarde rien directement et ne fait aucun rendu.
 */
final class CloudRegionMotionController {

    /**
     * Met à jour la position d'une région de nuage.
     *
     * @param level niveau serveur
     * @param state région de nuage à mettre à jour
     * @return true si la région a été modifiée
     */
    boolean tick(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        if (!state.isActive() || state.isEmpty()) {
            return false;
        }
        if (!isMovementEnabled() || isMovementFrozen()) {
            return false;
        }

        return tickState(
                state,
                level.getGameTime(),
                regionKey -> resolveWindVelocity(level, regionKey)
        );
    }

    /**
     * Applies one movement step without consulting configuration. Kept package
     * private so the authoritative motion contract can be tested without a
     * running Minecraft server.
     */
    static boolean tickState(
            @NotNull CloudRegionState state,
            long gameTime,
            @NotNull Function<RegionInstanceKey, Vec3> velocityResolver
    ) {
        if (!state.isActive() || state.isEmpty()) {
            return false;
        }

        Map<UUID, CloudClusterState> clustersById = new LinkedHashMap<>();
        List<MotionMember> members = new ArrayList<>();
        for (CloudClusterState cluster : state.getClusters()) {
            if (cluster == null || !cluster.isActive()) {
                continue;
            }
            clustersById.put(cluster.getClusterId(), cluster);
            members.add(new MotionMember(
                    cluster.getClusterId(),
                    cluster.getMorphologyGroupId(),
                    cluster.getMorphologyCount(),
                    cluster.getCenter()
            ));
        }

        MotionPlan plan = planMotion(
                members,
                state.getSourceRegionKey(),
                state.getCurrentRegionKey(),
                velocityResolver
        );
        if (plan.latchedMorphologyRegion() != null && state.getCurrentRegionKey() == null) {
            state.setCurrentRegionKey(plan.latchedMorphologyRegion());
        }

        boolean changed = false;
        for (GroupMotion groupMotion : plan.groupMotions()) {
            List<CloudClusterState> groupMembers = resolveClusters(groupMotion.memberIds(), clustersById);
            ShapeFingerprint before = shouldLogBoundaryDiagnostics(groupMembers, gameTime)
                    ? ShapeFingerprint.capture(groupMembers)
                    : null;
            changed |= applyCommonVelocity(groupMembers, groupMotion.velocity(), gameTime);
            if (before != null) {
                ShapeFingerprint after = ShapeFingerprint.capture(groupMembers);
                logBoundaryDiagnostics(
                        groupMotion.groupId(),
                        groupMembers,
                        groupMotion.sampledRegion(),
                        groupMotion.velocity(),
                        before,
                        after
                );
            }
        }

        for (StandaloneMotion standaloneMotion : plan.standaloneMotions()) {
            CloudClusterState cluster = clustersById.get(standaloneMotion.clusterId());
            if (cluster != null) {
                changed |= applyCommonVelocity(List.of(cluster), standaloneMotion.velocity(), gameTime);
            }
        }

        return changed;
    }

    /**
     * Builds the authoritative velocity plan from pure persistent identity and
     * position data. Multi-member morphology groups perform one source-region
     * lookup; standalone clusters retain their local-region lookup.
     */
    static MotionPlan planMotion(
            @NotNull List<MotionMember> members,
            @Nullable RegionInstanceKey sourceRegion,
            @Nullable RegionInstanceKey currentRegion,
            @NotNull Function<RegionInstanceKey, Vec3> velocityResolver
    ) {
        Map<UUID, List<MotionMember>> morphologyGroups = new LinkedHashMap<>();
        List<MotionMember> standaloneMembers = new ArrayList<>();
        for (MotionMember member : members) {
            if (member == null) {
                continue;
            }
            if (member.morphologyCount() > 1) {
                morphologyGroups
                        .computeIfAbsent(member.morphologyGroupId(), ignored -> new ArrayList<>())
                        .add(member);
            } else {
                standaloneMembers.add(member);
            }
        }

        RegionInstanceKey morphologyRegion = sourceRegion != null ? sourceRegion : currentRegion;
        RegionInstanceKey latchedRegion = null;
        if (morphologyRegion == null && !morphologyGroups.isEmpty()) {
            List<MotionMember> firstGroup = morphologyGroups.values().iterator().next();
            morphologyRegion = RegionInstanceKey.from(BlockPos.containing(arithmeticMemberCenter(firstGroup)));
            latchedRegion = morphologyRegion;
        }

        List<GroupMotion> groupMotions = new ArrayList<>(morphologyGroups.size());
        for (Map.Entry<UUID, List<MotionMember>> entry : morphologyGroups.entrySet()) {
            Vec3 velocity = safeVelocity(velocityResolver.apply(morphologyRegion));
            List<UUID> memberIds = entry.getValue().stream()
                    .map(MotionMember::clusterId)
                    .toList();
            groupMotions.add(new GroupMotion(
                    entry.getKey(),
                    morphologyRegion,
                    velocity,
                    memberIds
            ));
        }

        List<StandaloneMotion> standaloneMotions = new ArrayList<>(standaloneMembers.size());
        for (MotionMember member : standaloneMembers) {
            RegionInstanceKey localRegion = RegionInstanceKey.from(BlockPos.containing(member.center()));
            Vec3 velocity = safeVelocity(velocityResolver.apply(localRegion));
            standaloneMotions.add(new StandaloneMotion(member.clusterId(), localRegion, velocity));
        }

        return new MotionPlan(
                List.copyOf(groupMotions),
                List.copyOf(standaloneMotions),
                latchedRegion
        );
    }

    static boolean isMovementFrozen() {
        try {
            return AtmosphereConfig.clouds().freezeCloudMovement();
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private boolean isMovementEnabled() {
        try {
            return AtmosphereConfig.clouds().cloudMovementEnabled();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private static boolean applyCommonVelocity(
            @NotNull List<CloudClusterState> clusters,
            @NotNull Vec3 velocity,
            long gameTime
    ) {
        if (velocity.lengthSqr() <= 0.0000001D) {
            for (CloudClusterState cluster : clusters) {
                cluster.setVelocity(Vec3.ZERO);
            }
            return false;
        }

        for (CloudClusterState cluster : clusters) {
            Vec3 currentCenter = cluster.getCenter();
            cluster.setPreviousCenter(currentCenter);
            cluster.setCenter(currentCenter.add(velocity));
            cluster.setVelocity(velocity);
            cluster.setLastMotionTick(gameTime);
        }
        return !clusters.isEmpty();
    }

    private static Vec3 safeVelocity(Vec3 velocity) {
        if (velocity == null
                || !Double.isFinite(velocity.x())
                || !Double.isFinite(velocity.y())
                || !Double.isFinite(velocity.z())) {
            return Vec3.ZERO;
        }
        return velocity;
    }

    private static List<CloudClusterState> resolveClusters(
            List<UUID> clusterIds,
            Map<UUID, CloudClusterState> clustersById
    ) {
        List<CloudClusterState> resolved = new ArrayList<>(clusterIds.size());
        for (UUID clusterId : clusterIds) {
            CloudClusterState cluster = clustersById.get(clusterId);
            if (cluster != null) {
                resolved.add(cluster);
            }
        }
        return resolved;
    }

    private static boolean shouldLogBoundaryDiagnostics(List<CloudClusterState> members, long gameTime) {
        if (members.size() < 2 || Math.floorMod(gameTime, 200L) != 0L) {
            return false;
        }
        Set<RegionInstanceKey> occupiedRegions = new HashSet<>();
        for (CloudClusterState member : members) {
            occupiedRegions.add(RegionInstanceKey.from(BlockPos.containing(member.getCenter())));
            if (occupiedRegions.size() > 1) {
                return true;
            }
        }
        return false;
    }

    private static void logBoundaryDiagnostics(
            UUID groupId,
            List<CloudClusterState> members,
            RegionInstanceKey sampledRegion,
            Vec3 velocity,
            ShapeFingerprint before,
            ShapeFingerprint after
    ) {
        Set<RegionInstanceKey> occupiedRegions = new HashSet<>();
        for (CloudClusterState member : members) {
            occupiedRegions.add(RegionInstanceKey.from(BlockPos.containing(member.getCenter())));
        }
        ProjectAtmosphere.LOGGER.info(
                "[NativeCloudMotion] rigidGroup={} members={} sampledRegion={} occupiedRegions={}"
                        + " centroid=({},{}) velocity=({},{}) offsetHash={}->{} maxOffsetDrift={}",
                groupId,
                members.size(),
                sampledRegion,
                occupiedRegions.size(),
                formatSix(after.center().x()),
                formatSix(after.center().z()),
                formatSix(velocity.x()),
                formatSix(velocity.z()),
                Long.toUnsignedString(before.hash(), 16),
                Long.toUnsignedString(after.hash(), 16),
                formatNine(before.maxOffsetDrift(after))
        );
    }

    private static Vec3 arithmeticCenter(List<CloudClusterState> members) {
        if (members.isEmpty()) {
            return Vec3.ZERO;
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (CloudClusterState member : members) {
            x += member.getCenter().x();
            y += member.getCenter().y();
            z += member.getCenter().z();
        }
        double inverseCount = 1.0D / members.size();
        return new Vec3(x * inverseCount, y * inverseCount, z * inverseCount);
    }

    private static Vec3 arithmeticMemberCenter(List<MotionMember> members) {
        if (members.isEmpty()) {
            return Vec3.ZERO;
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (MotionMember member : members) {
            x += member.center().x();
            y += member.center().y();
            z += member.center().z();
        }
        double inverseCount = 1.0D / members.size();
        return new Vec3(x * inverseCount, y * inverseCount, z * inverseCount);
    }

    private static String formatSix(double value) {
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private static String formatNine(double value) {
        return String.format(java.util.Locale.ROOT, "%.9f", value);
    }

    private Vec3 resolveWindVelocity(@NotNull ServerLevel level, @NotNull RegionInstanceKey regionKey) {
        WindVector wind = ForecastOrchestrator.getWind(regionKey, level.getGameTime());
        if (wind == null || wind.baseSpeed() <= 0.0F) {
            return Vec3.ZERO;
        }

        double scale = getDriftScale();
        double speed = Math.max(0.0D, wind.baseSpeed()) * scale;
        double angle = wind.angleRadians();
        return new Vec3(-Math.sin(angle) * speed, 0.0D, Math.cos(angle) * speed);
    }

    private double getDriftScale() {
        try {
            return AtmosphereConfig.clouds().cloudWindDriftScale();
        } catch (IllegalStateException exception) {
            return 0.035D;
        }
    }

    record MotionMember(UUID clusterId, UUID morphologyGroupId, int morphologyCount, Vec3 center) {
    }

    record GroupMotion(UUID groupId, RegionInstanceKey sampledRegion, Vec3 velocity, List<UUID> memberIds) {
    }

    record StandaloneMotion(UUID clusterId, RegionInstanceKey sampledRegion, Vec3 velocity) {
    }

    record MotionPlan(
            List<GroupMotion> groupMotions,
            List<StandaloneMotion> standaloneMotions,
            @Nullable RegionInstanceKey latchedMorphologyRegion
    ) {
    }

    private record ShapeFingerprint(Vec3 center, Map<UUID, Vec3> offsets, long hash) {
        private static final long FNV_OFFSET = 0xcbf29ce484222325L;
        private static final long FNV_PRIME = 0x100000001b3L;

        private static ShapeFingerprint capture(List<CloudClusterState> members) {
            List<CloudClusterState> sorted = new ArrayList<>(members);
            sorted.sort(Comparator.comparing(CloudClusterState::getClusterId));
            Vec3 center = arithmeticCenter(sorted);
            Map<UUID, Vec3> offsets = new HashMap<>();
            long hash = FNV_OFFSET;
            for (CloudClusterState member : sorted) {
                Vec3 offset = member.getCenter().subtract(center);
                offsets.put(member.getClusterId(), offset);
                hash = mix(hash, member.getClusterId().getMostSignificantBits());
                hash = mix(hash, member.getClusterId().getLeastSignificantBits());
                hash = mix(hash, Math.round(offset.x() * 1_000_000.0D));
                hash = mix(hash, Math.round(offset.y() * 1_000_000.0D));
                hash = mix(hash, Math.round(offset.z() * 1_000_000.0D));
            }
            return new ShapeFingerprint(center, Map.copyOf(offsets), hash);
        }

        private double maxOffsetDrift(ShapeFingerprint other) {
            double maximum = 0.0D;
            for (Map.Entry<UUID, Vec3> entry : offsets.entrySet()) {
                Vec3 otherOffset = other.offsets.get(entry.getKey());
                if (otherOffset == null) {
                    return Double.POSITIVE_INFINITY;
                }
                maximum = Math.max(maximum, entry.getValue().distanceTo(otherOffset));
            }
            return maximum;
        }

        private static long mix(long hash, long value) {
            long mixed = hash;
            for (int byteIndex = 0; byteIndex < Long.BYTES; byteIndex++) {
                mixed ^= (value >>> (byteIndex * Byte.SIZE)) & 0xffL;
                mixed *= FNV_PRIME;
            }
            return mixed;
        }
    }
}

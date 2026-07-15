package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Deterministic checks for authoritative native-cloud group motion. */
public final class CloudRegionMotionSandbox {
    private static final RegionInstanceKey NEGATIVE_X_REGION = new RegionInstanceKey(-1, -1);

    private CloudRegionMotionSandbox() {
    }

    public static void main(String[] args) {
        convergentBoundaryPreservesMorphologyOffsets();
        standaloneClustersStillUseTheirLocalRegions();
        legacyGroupLatchesOneFallbackRegion();
        System.out.println("Cloud region motion self-check passed.");
    }

    private static void convergentBoundaryPreservesMorphologyOffsets() {
        UUID groupId = new UUID(17L, 29L);
        UUID primaryId = new UUID(17L, 29L);
        List<CloudRegionMotionController.MotionMember> members = List.of(
                member(primaryId, groupId, 3, -25.0D, 256.0D, -700.0D),
                member(new UUID(17L, 30L), groupId, 3, 25.0D, 270.0D, -710.0D),
                member(new UUID(17L, 31L), groupId, 3, -5.0D, 305.0D, -680.0D)
        );
        Map<UUID, Vec3> initialOffsets = offsetsFrom(primaryId, members);
        AtomicInteger lookups = new AtomicInteger();
        Function<RegionInstanceKey, Vec3> convergentWind = key -> {
            lookups.incrementAndGet();
            return key.regionX() < 0
                    ? new Vec3(1.0D, 0.0D, -0.25D)
                    : new Vec3(-5.0D, 0.0D, 0.75D);
        };

        for (int tick = 1; tick <= 400; tick++) {
            CloudRegionMotionController.MotionPlan plan = CloudRegionMotionController.planMotion(
                    members,
                    NEGATIVE_X_REGION,
                    NEGATIVE_X_REGION,
                    convergentWind
            );
            assertEquals("group motion count", 1, plan.groupMotions().size());
            assertEquals("standalone motion count", 0, plan.standaloneMotions().size());
            members = applyPlan(members, plan);
        }

        assertVec("primary translation", new Vec3(375.0D, 256.0D, -800.0D), centerOf(primaryId, members), 0.0D);
        assertOffsets("convergent boundary offsets", initialOffsets, offsetsFrom(primaryId, members), 0.0D);
        assertEquals("one lookup per group tick", 400, lookups.get());
    }

    private static void standaloneClustersStillUseTheirLocalRegions() {
        UUID leftId = new UUID(23L, 1L);
        UUID rightId = new UUID(23L, 2L);
        List<CloudRegionMotionController.MotionMember> members = List.of(
                member(leftId, leftId, 1, -10.0D, 256.0D, -700.0D),
                member(rightId, rightId, 1, 10.0D, 256.0D, -700.0D)
        );
        Function<RegionInstanceKey, Vec3> convergentWind = key -> key.regionX() < 0
                ? new Vec3(0.5D, 0.0D, 0.0D)
                : new Vec3(-0.75D, 0.0D, 0.0D);

        CloudRegionMotionController.MotionPlan plan = CloudRegionMotionController.planMotion(
                members,
                NEGATIVE_X_REGION,
                NEGATIVE_X_REGION,
                convergentWind
        );
        assertEquals("standalone count", 2, plan.standaloneMotions().size());
        List<CloudRegionMotionController.MotionMember> moved = applyPlan(members, plan);
        assertVec("standalone left", new Vec3(-9.5D, 256.0D, -700.0D), centerOf(leftId, moved), 0.0D);
        assertVec("standalone right", new Vec3(9.25D, 256.0D, -700.0D), centerOf(rightId, moved), 0.0D);
    }

    private static void legacyGroupLatchesOneFallbackRegion() {
        UUID groupId = new UUID(41L, 1L);
        UUID primaryId = new UUID(41L, 1L);
        List<CloudRegionMotionController.MotionMember> members = List.of(
                member(primaryId, groupId, 2, -1.0D, 256.0D, -700.0D),
                member(new UUID(41L, 2L), groupId, 2, 0.0D, 270.0D, -700.0D)
        );
        Function<RegionInstanceKey, Vec3> convergentWind = key -> key.regionX() < 0
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(-3.0D, 0.0D, 0.0D);
        RegionInstanceKey currentRegion = null;

        for (int tick = 1; tick <= 20; tick++) {
            CloudRegionMotionController.MotionPlan plan = CloudRegionMotionController.planMotion(
                    members,
                    null,
                    currentRegion,
                    convergentWind
            );
            if (currentRegion == null) {
                currentRegion = plan.latchedMorphologyRegion();
            }
            members = applyPlan(members, plan);
        }

        if (!NEGATIVE_X_REGION.equals(currentRegion)) {
            throw new IllegalStateException("legacy fallback region was not latched: " + currentRegion);
        }
        assertVec("legacy primary", new Vec3(19.0D, 256.0D, -700.0D), centerOf(primaryId, members), 0.0D);
    }

    private static CloudRegionMotionController.MotionMember member(
            UUID clusterId,
            UUID groupId,
            int groupCount,
            double x,
            double y,
            double z
    ) {
        return new CloudRegionMotionController.MotionMember(
                clusterId,
                groupId,
                groupCount,
                new Vec3(x, y, z)
        );
    }

    private static List<CloudRegionMotionController.MotionMember> applyPlan(
            List<CloudRegionMotionController.MotionMember> members,
            CloudRegionMotionController.MotionPlan plan
    ) {
        Map<UUID, Vec3> velocities = new HashMap<>();
        for (CloudRegionMotionController.GroupMotion group : plan.groupMotions()) {
            for (UUID memberId : group.memberIds()) {
                velocities.put(memberId, group.velocity());
            }
        }
        for (CloudRegionMotionController.StandaloneMotion standalone : plan.standaloneMotions()) {
            velocities.put(standalone.clusterId(), standalone.velocity());
        }

        List<CloudRegionMotionController.MotionMember> moved = new ArrayList<>(members.size());
        for (CloudRegionMotionController.MotionMember member : members) {
            Vec3 velocity = velocities.getOrDefault(member.clusterId(), Vec3.ZERO);
            moved.add(new CloudRegionMotionController.MotionMember(
                    member.clusterId(),
                    member.morphologyGroupId(),
                    member.morphologyCount(),
                    member.center().add(velocity)
            ));
        }
        return List.copyOf(moved);
    }

    private static Vec3 centerOf(UUID id, List<CloudRegionMotionController.MotionMember> members) {
        for (CloudRegionMotionController.MotionMember member : members) {
            if (member.clusterId().equals(id)) {
                return member.center();
            }
        }
        throw new IllegalStateException("missing member " + id);
    }

    private static Map<UUID, Vec3> offsetsFrom(
            UUID referenceId,
            List<CloudRegionMotionController.MotionMember> members
    ) {
        Vec3 reference = centerOf(referenceId, members);
        Map<UUID, Vec3> offsets = new HashMap<>();
        for (CloudRegionMotionController.MotionMember member : members) {
            offsets.put(member.clusterId(), member.center().subtract(reference));
        }
        return offsets;
    }

    private static void assertOffsets(String label, Map<UUID, Vec3> expected, Map<UUID, Vec3> actual, double tolerance) {
        for (Map.Entry<UUID, Vec3> entry : expected.entrySet()) {
            Vec3 actualOffset = actual.get(entry.getKey());
            if (actualOffset == null) {
                throw new IllegalStateException(label + " missing cluster " + entry.getKey());
            }
            assertVec(label + " " + entry.getKey(), entry.getValue(), actualOffset, tolerance);
        }
    }

    private static void assertVec(String label, Vec3 expected, Vec3 actual, double tolerance) {
        if (expected.distanceTo(actual) > tolerance) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + actual);
        }
    }
}

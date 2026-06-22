package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless deterministic cloudlet layout generator. Cloudlet identity and
 * local shape stay stable because they derive only from field seed + id.
 */
public final class CloudletLayout {
    private CloudletLayout() {
    }

    public static List<Cloudlet> generate(CloudFieldSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasVisibleClouds()) {
            return List.of();
        }

        int count = snapshot.dynamicCloudletCount();
        if (count <= 0) {
            return List.of();
        }

        List<Cloudlet> cloudlets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cloudlets.add(generate(snapshot, CloudletId.of(i)));
        }
        return List.copyOf(cloudlets);
    }

    public static Cloudlet generate(CloudFieldSnapshot snapshot, CloudletId id) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot cannot be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }

        long seed = id.mixedSeed(snapshot.seed());
        float angle = unit(seed, 1) * (float) (Math.PI * 2.0);
        float ring = (float) Math.sqrt(unit(seed, 2)) * snapshot.radius() * 0.82F;
        float height01 = 0.12F + unit(seed, 3) * 0.76F;
        float vertical = snapshot.baseY() + (snapshot.topY() - snapshot.baseY()) * height01;
        float radiusScale = lerp(0.10F, 0.24F, unit(seed, 4)) * lerp(1.05F, 0.64F, height01);
        float horizontalRadius = Math.max(1.0F, snapshot.radius() * radiusScale);
        float verticalScale = lerp(0.62F, 1.18F, unit(seed, 5));
        float densityScale = lerp(0.72F, 1.12F, unit(seed, 6));
        float coverageWeight = lerp(0.58F, 1.00F, unit(seed, 7))
                * snapshot.lodBand().detailScale()
                * snapshot.hydrationProgress();
        long coherentAgeTicks = Math.max(0L, snapshot.fieldAgeTicks() - Math.round(unit(seed, 8) * 2400.0F));

        Vec3 localOffset = new Vec3(
                Math.cos(angle) * ring,
                vertical - snapshot.center().y(),
                Math.sin(angle) * ring
        );

        return new Cloudlet(
                id,
                localOffset,
                horizontalRadius,
                verticalScale,
                densityScale,
                coverageWeight,
                coherentAgeTicks
        );
    }

    private static float unit(long seed, int salt) {
        long value = seed + (long) salt * 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (float) ((value >>> 40) & 0xFFFFFFL) / (float) 0xFFFFFF;
    }

    private static float lerp(float min, float max, float t) {
        return min + (max - min) * Math.max(0.0F, Math.min(1.0F, t));
    }

    public record Cloudlet(
            CloudletId id,
            Vec3 localOffset,
            float horizontalRadius,
            float verticalScale,
            float densityScale,
            float coverageWeight,
            long coherentAgeTicks
    ) {
        public Vec3 worldCenter(CloudFieldSnapshot snapshot) {
            return snapshot.center().add(localOffset);
        }
    }
}

package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Maps camera distance to the field LOD band. Range values are supplied by a
 * small config object so the defaults are not spread across the system.
 */
public final class CloudFieldDistanceClassifier {
    private final Ranges ranges;

    public CloudFieldDistanceClassifier(Ranges ranges) {
        this.ranges = Objects.requireNonNull(ranges, "ranges");
    }

    public static CloudFieldDistanceClassifier defaultClassifier() {
        return new CloudFieldDistanceClassifier(Ranges.DEFAULT);
    }

    public Ranges ranges() {
        return ranges;
    }

    public CloudLodBand classify(CloudField field, Vec3 cameraPosition) {
        Objects.requireNonNull(field, "field");
        return classifyDistance(edgeDistance(field.center(), field.radius(), cameraPosition));
    }

    public CloudLodBand classify(Vec3 center, float radius, Vec3 cameraPosition) {
        return classifyDistance(edgeDistance(center, radius, cameraPosition));
    }

    public CloudLodBand classifyDistance(double distanceBlocks) {
        double distance = Math.max(0.0D, finite(distanceBlocks, 0.0D));
        if (distance <= ranges.dynamicEndBlocks()) {
            return CloudLodBand.DYNAMIC;
        }
        if (distance <= ranges.transitionEndBlocks()) {
            return CloudLodBand.TRANSITION;
        }
        if (distance <= ranges.farProceduralEndBlocks()) {
            return CloudLodBand.FAR_PROCEDURAL;
        }
        return CloudLodBand.HAZE;
    }

    public double edgeDistance(Vec3 center, float radius, Vec3 cameraPosition) {
        Vec3 fieldCenter = center == null ? Vec3.ZERO : center;
        Vec3 camera = cameraPosition == null ? Vec3.ZERO : cameraPosition;
        return Math.max(0.0D, fieldCenter.distanceTo(camera) - Math.max(0.0F, radius));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    public record Ranges(
            double dynamicEndBlocks,
            double transitionEndBlocks,
            double farProceduralEndBlocks
    ) {
        public static final Ranges DEFAULT = new Ranges(500.0D, 1200.0D, 2000.0D);

        public Ranges {
            dynamicEndBlocks = finite(dynamicEndBlocks, 500.0D);
            transitionEndBlocks = finite(transitionEndBlocks, 1200.0D);
            farProceduralEndBlocks = finite(farProceduralEndBlocks, 2000.0D);
            if (dynamicEndBlocks < 0.0D) {
                throw new IllegalArgumentException("dynamicEndBlocks must be non-negative");
            }
            if (transitionEndBlocks <= dynamicEndBlocks) {
                throw new IllegalArgumentException("transitionEndBlocks must be greater than dynamicEndBlocks");
            }
            if (farProceduralEndBlocks <= transitionEndBlocks) {
                throw new IllegalArgumentException("farProceduralEndBlocks must be greater than transitionEndBlocks");
            }
        }
    }
}

package net.Gabou.projectatmosphere.clouds.backend;

import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public record CloudBackendBridgeSnapshot(
        String sourceBackend,
        String sourceTypeId,
        String sourceMorphologyFamily,
        String sourceTrackingKey,
        double x,
        double y,
        double z,
        double radius,
        double height,
        double density,
        double coverage,
        double stormStrength,
        long capturedGameTime
) {
    private static final String SOURCE_BACKEND = "SourceBackend";
    private static final String SOURCE_TYPE_ID = "SourceTypeId";
    private static final String SOURCE_MORPHOLOGY_FAMILY = "SourceMorphologyFamily";
    private static final String SOURCE_TRACKING_KEY = "SourceTrackingKey";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final String RADIUS = "Radius";
    private static final String HEIGHT = "Height";
    private static final String DENSITY = "Density";
    private static final String COVERAGE = "Coverage";
    private static final String STORM_STRENGTH = "StormStrength";
    private static final String CAPTURED_GAME_TIME = "CapturedGameTime";

    public static @NotNull CloudBackendBridgeSnapshot fromPaRegion(@NotNull CloudRegionState region, long gameTime) {
        Vec3 center = region.getCenter();
        return new CloudBackendBridgeSnapshot(
                CloudVisualBackend.PA_NATIVE.name(),
                region.getCloudTypeId(),
                region.getMorphologyFamily().name(),
                region.getRegionId().toString(),
                center.x(),
                center.y(),
                center.z(),
                Math.max(1.0D, region.getRadius()),
                Math.max(1.0D, region.getTopY() - region.getBaseY()),
                clamp01(region.getDensity()),
                clamp01(region.getCoverage()),
                estimateStormStrength(region.getCloudTypeId(), region.getMorphologyFamily().name()),
                gameTime
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(SOURCE_BACKEND, safe(sourceBackend));
        tag.putString(SOURCE_TYPE_ID, safe(sourceTypeId));
        tag.putString(SOURCE_MORPHOLOGY_FAMILY, safe(sourceMorphologyFamily));
        tag.putString(SOURCE_TRACKING_KEY, safe(sourceTrackingKey));
        tag.putDouble(X, x);
        tag.putDouble(Y, y);
        tag.putDouble(Z, z);
        tag.putDouble(RADIUS, Math.max(1.0D, radius));
        tag.putDouble(HEIGHT, Math.max(1.0D, height));
        tag.putDouble(DENSITY, clamp01(density));
        tag.putDouble(COVERAGE, clamp01(coverage));
        tag.putDouble(STORM_STRENGTH, clamp01(stormStrength));
        tag.putLong(CAPTURED_GAME_TIME, capturedGameTime);
        return tag;
    }

    public static @NotNull CloudBackendBridgeSnapshot load(@NotNull CompoundTag tag) {
        return new CloudBackendBridgeSnapshot(
                tag.contains(SOURCE_BACKEND, Tag.TAG_STRING) ? tag.getString(SOURCE_BACKEND) : "",
                tag.contains(SOURCE_TYPE_ID, Tag.TAG_STRING) ? tag.getString(SOURCE_TYPE_ID) : "",
                tag.contains(SOURCE_MORPHOLOGY_FAMILY, Tag.TAG_STRING) ? tag.getString(SOURCE_MORPHOLOGY_FAMILY) : "",
                tag.contains(SOURCE_TRACKING_KEY, Tag.TAG_STRING) ? tag.getString(SOURCE_TRACKING_KEY) : "",
                tag.getDouble(X),
                tag.getDouble(Y),
                tag.getDouble(Z),
                tag.contains(RADIUS) ? tag.getDouble(RADIUS) : 128.0D,
                tag.contains(HEIGHT) ? tag.getDouble(HEIGHT) : 48.0D,
                tag.contains(DENSITY) ? tag.getDouble(DENSITY) : 0.65D,
                tag.contains(COVERAGE) ? tag.getDouble(COVERAGE) : 0.65D,
                tag.contains(STORM_STRENGTH) ? tag.getDouble(STORM_STRENGTH) : 0.0D,
                tag.getLong(CAPTURED_GAME_TIME)
        );
    }

    private static double estimateStormStrength(String typeId, String morphologyFamily) {
        String type = safe(typeId).toLowerCase(java.util.Locale.ROOT);
        String family = safe(morphologyFamily).toLowerCase(java.util.Locale.ROOT);
        if (type.contains("cumulonimbus") || family.contains("storm")) {
            return 0.9D;
        }
        if (type.contains("nimbus") || type.contains("congestus") || family.contains("tower")) {
            return 0.55D;
        }
        return 0.0D;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

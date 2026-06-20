package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.List;

public final class CloudShadowRenderer {
    private static final int DEFAULT_GRID_RESOLUTION = 64;
    private static final long SHADOW_REFRESH_TICKS = 20L;
    private static final double SHADOW_REFRESH_CAMERA_DISTANCE_SQ = 16.0D * 16.0D;
    private static final float MIN_SHADOW_BOUNDS_RADIUS = 256.0F;
    private static final float SHADOW_FADE_RADIUS_SCALE = 1.58F;
    private static final float MAX_CELL_ADDITIVE_SHADOW = 0.92F;
    private static long lastPublishedWorldTime = Long.MIN_VALUE;
    private static Vec3 lastPublishedCameraPosition;
    private static int lastPublishedSnapshotCount = -1;

    private CloudShadowRenderer() {
    }

    public static void update(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull List<CloudRenderSnapshot> snapshots
    ) {
        if (!isEnabled() || snapshots.isEmpty()) {
            CloudShadowMapAccess.clear();
            resetCache();
            return;
        }

        if (!shouldRefresh(frameContext, snapshots)) {
            return;
        }

        ShadowBounds bounds = resolveBounds(frameContext, snapshots);
        if (!bounds.isValid()) {
            CloudShadowMapAccess.clear();
            resetCache();
            return;
        }

        int resolution = DEFAULT_GRID_RESOLUTION;
        float[] values = new float[resolution * resolution];
        for (int z = 0; z < resolution; z++) {
            float worldZ = Mth.lerp(z / (float) (resolution - 1), bounds.minZ(), bounds.maxZ());
            for (int x = 0; x < resolution; x++) {
                float worldX = Mth.lerp(x / (float) (resolution - 1), bounds.minX(), bounds.maxX());
                values[z * resolution + x] = sampleCloudOcclusion(snapshots, worldX, worldZ);
            }
        }

        Matrix4f worldToShadow = new Matrix4f()
                .identity()
                .translate(-bounds.minX(), 0.0F, -bounds.minZ())
                .scale(1.0F / Math.max(1.0F, bounds.maxX() - bounds.minX()), 1.0F, 1.0F / Math.max(1.0F, bounds.maxZ() - bounds.minZ()));
        CloudShadowMapAccess.publishSnapshot(new CloudShadowSnapshot(
                true,
                -1,
                resolution,
                resolution,
                bounds.minX(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxZ(),
                worldToShadow,
                frameContext.getWorldTime(),
                values
        ));
        lastPublishedWorldTime = frameContext.getWorldTime();
        lastPublishedCameraPosition = frameContext.getCameraPosition();
        lastPublishedSnapshotCount = snapshots.size();
    }

    public static void clear() {
        CloudShadowMapAccess.clear();
        resetCache();
    }

    private static boolean shouldRefresh(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull List<CloudRenderSnapshot> snapshots
    ) {
        long worldTime = frameContext.getWorldTime();
        if (lastPublishedWorldTime == Long.MIN_VALUE
                || worldTime - lastPublishedWorldTime >= SHADOW_REFRESH_TICKS
                || snapshots.size() != lastPublishedSnapshotCount
                || lastPublishedCameraPosition == null) {
            return true;
        }

        return frameContext.getCameraPosition().distanceToSqr(lastPublishedCameraPosition) >= SHADOW_REFRESH_CAMERA_DISTANCE_SQ;
    }

    private static void resetCache() {
        lastPublishedWorldTime = Long.MIN_VALUE;
        lastPublishedCameraPosition = null;
        lastPublishedSnapshotCount = -1;
    }

    private static float sampleCloudOcclusion(@NotNull List<CloudRenderSnapshot> snapshots, float worldX, float worldZ) {
        float accumulated = 0.0F;
        for (CloudRenderSnapshot snapshot : snapshots) {
            Vec3 center = snapshot.getRegionCenter();
            if (center == null) {
                continue;
            }

            float dx = worldX - (float) center.x();
            float dz = worldZ - (float) center.z();
            float radius = Math.max(1.0F, snapshot.getRegionRadius() * SHADOW_FADE_RADIUS_SCALE);
            float distanceNorm = Mth.sqrt(dx * dx + dz * dz) / radius;
            if (distanceNorm >= 1.0F) {
                continue;
            }

            float contribution = CloudDensityProvider.sampleShadowDensity(snapshot, worldX, worldZ);

            accumulated = 1.0F - ((1.0F - accumulated) * (1.0F - contribution));
            if (accumulated >= MAX_CELL_ADDITIVE_SHADOW) {
                return MAX_CELL_ADDITIVE_SHADOW;
            }
        }
        return Mth.clamp(accumulated, 0.0F, MAX_CELL_ADDITIVE_SHADOW);
    }

    private static ShadowBounds resolveBounds(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull List<CloudRenderSnapshot> snapshots
    ) {
        Vec3 camera = frameContext.getCameraPosition();
        float minX = (float) camera.x() - MIN_SHADOW_BOUNDS_RADIUS;
        float maxX = (float) camera.x() + MIN_SHADOW_BOUNDS_RADIUS;
        float minZ = (float) camera.z() - MIN_SHADOW_BOUNDS_RADIUS;
        float maxZ = (float) camera.z() + MIN_SHADOW_BOUNDS_RADIUS;

        for (CloudRenderSnapshot snapshot : snapshots) {
            Vec3 center = snapshot.getRegionCenter();
            if (center == null) {
                continue;
            }

            float radius = Math.max(1.0F, snapshot.getRegionRadius() * SHADOW_FADE_RADIUS_SCALE);
            minX = Math.min(minX, (float) center.x() - radius);
            maxX = Math.max(maxX, (float) center.x() + radius);
            minZ = Math.min(minZ, (float) center.z() - radius);
            maxZ = Math.max(maxZ, (float) center.z() + radius);
        }

        return new ShadowBounds(minX, minZ, maxX, maxZ);
    }

    private static boolean isEnabled() {
        try {
            return AtmoCommonConfig.ENABLE_CLOUD_SHADOW_MAP.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record ShadowBounds(float minX, float minZ, float maxX, float maxZ) {
        boolean isValid() {
            return maxX > minX && maxZ > minZ;
        }
    }
}

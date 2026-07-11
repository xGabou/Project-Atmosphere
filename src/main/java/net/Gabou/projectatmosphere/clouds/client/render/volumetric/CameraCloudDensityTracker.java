package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.CloudCellDensityMath;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Tracks the analytic cloud density at the camera each frame and smooths it.
 * Drives the in-cloud whiteout fog so terrain and entities fade exactly where
 * the raymarched volume becomes opaque.
 */
public final class CameraCloudDensityTracker {
    private static volatile float smoothedDensity;
    private static volatile long lastUpdateNanos;

    private CameraCloudDensityTracker() {
    }

    public static void update(List<CloudCell> cells, Vec3 cameraPos) {
        float raw = cells == null || cells.isEmpty()
                ? 0.0F
                : CloudCellDensityMath.densityAt(cells, cameraPos.x(), cameraPos.y(), cameraPos.z());

        update(raw);
    }

    public static void update(float rawDensity) {
        float raw = Mth.clamp(rawDensity, 0.0F, 1.0F);

        long now = System.nanoTime();
        float deltaSeconds = lastUpdateNanos == 0L
                ? 0.05F
                : Mth.clamp((now - lastUpdateNanos) / 1.0e9F, 0.001F, 0.25F);
        lastUpdateNanos = now;

        // Fast attack, slower release: entering a cloud whites out promptly,
        // leaving it clears smoothly.
        float rate = raw > smoothedDensity ? 6.0F : 2.5F;
        float blend = 1.0F - (float) Math.exp(-rate * deltaSeconds);
        smoothedDensity = Mth.lerp(blend, smoothedDensity, raw);
    }

    /** Smoothed in-cloud density at the camera, 0..1. */
    public static float smoothedCameraDensity() {
        return smoothedDensity;
    }

    public static void reset() {
        smoothedDensity = 0.0F;
        lastUpdateNanos = 0L;
    }
}

package net.Gabou.projectatmosphere.clouds.frontend;

import net.Gabou.projectatmosphere.clouds.backend.CloudRegionRenderData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Builds immutable frontend cloud render snapshots from transport safe cloud region data.
 * This class belongs to the frontend layer and does not read CloudRegionState directly.
 */
public final class CloudRenderSnapshotBuilder {

    private static final float DEFAULT_WIND_OFFSET_X = 0.0F;
    private static final float DEFAULT_WIND_OFFSET_Z = 0.0F;

    private CloudRenderSnapshotBuilder() {

    }

    /**
     * Creates a frontend render snapshot from transport safe cloud region data.
     *
     * @param renderData transport safe cloud region data
     * @param worldTime current client world time
     * @param partialTick current render partial tick
     * @param cameraPosition current camera position
     * @return immutable cloud render snapshot
     */
    public static @NotNull CloudRenderSnapshot create(
            @NotNull CloudRegionRenderData renderData,
            long worldTime,
            float partialTick,
            @NotNull Vec3 cameraPosition
    ) {
        Vec3 previousCenter = renderData.getPreviousCenter();
        Vec3 currentCenter = renderData.getCenter();

        Vec3 interpolatedCenter = previousCenter.lerp(
                currentCenter,
                partialTick
        );

        return new CloudRenderSnapshot(
                renderData.isActive(),
                renderData.getDimensionId(),
                worldTime,
                partialTick,
                cameraPosition,
                interpolatedCenter,
                previousCenter,
                renderData.getVelocity(),
                renderData.getRadius(),
                renderData.getBaseY(),
                renderData.getTopY(),
                renderData.getDensity(),
                renderData.getCoverage(),
                renderData.getEdgeSoftness(),
                DEFAULT_WIND_OFFSET_X,
                DEFAULT_WIND_OFFSET_Z,
                renderData.getAgeTicks(),
                renderData.getLifetimeTicks(),
                renderData.getGrowth(),
                renderData.getDecay(),
                renderData.getDebugColorOrTint()
        );
    }
}
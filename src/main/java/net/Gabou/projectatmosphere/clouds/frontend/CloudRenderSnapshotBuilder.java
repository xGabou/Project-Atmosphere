package net.Gabou.projectatmosphere.clouds.frontend;

import net.Gabou.projectatmosphere.clouds.backend.CloudRegionRenderData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Construit des snapshots frontend immutables depuis les données de région transportables.
 * Cette classe appartient à la couche frontend et ne lit jamais CloudRegionState directement.
 */
public final class CloudRenderSnapshotBuilder {

    private static final float DEFAULT_WIND_OFFSET_X = 0.0F;
    private static final float DEFAULT_WIND_OFFSET_Z = 0.0F;

    private CloudRenderSnapshotBuilder() {

    }

    /**
     * Crée un snapshot de rendu frontend depuis une donnée de région transportable.
     *
     * @param renderData donnée de région de nuage transportable
     * @param worldTime temps monde client courant
     * @param partialTick interpolation de rendu courante
     * @param cameraPosition position actuelle de la caméra
     * @return snapshot de rendu de nuage immutable
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

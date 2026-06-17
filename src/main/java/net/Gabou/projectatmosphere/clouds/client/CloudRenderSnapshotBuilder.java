package net.Gabou.projectatmosphere.clouds.client;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
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
        Vec3 velocity = renderData.getVelocity();
        long simulationTick = renderData.getSimulationTick();

        Vec3 snapshotPreviousCenter;
        Vec3 interpolatedCenter;
        if (simulationTick > 0L && velocity != null && velocity.lengthSqr() > 0.0000001D) {
            double elapsedTicks = Math.max(0.0D, Math.min(24.0D, (double) (worldTime - simulationTick) + partialTick));
            interpolatedCenter = currentCenter.add(velocity.scale(elapsedTicks));
            snapshotPreviousCenter = currentCenter.add(velocity.scale(Math.max(0.0D, elapsedTicks - 1.0D)));
        } else {
            interpolatedCenter = previousCenter.lerp(
                    currentCenter,
                    partialTick
            );
            snapshotPreviousCenter = previousCenter;
        }

        return new CloudRenderSnapshot(
                renderData.isActive(),
                renderData.getDimensionId(),
                worldTime,
                partialTick,
                cameraPosition,
                interpolatedCenter,
                snapshotPreviousCenter,
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
                renderData.getCloudTypeId(),
                renderData.getPreviousCloudTypeId(),
                renderData.getMorphologyFamily(),
                renderData.getCloudTypeTicks(),
                renderData.getVerticalThickness(),
                renderData.getEdgeErosionStrength(),
                renderData.getTopSoftness(),
                renderData.getBaseSoftness(),
                renderData.getBaseDarkness(),
                renderData.getNoiseScale(),
                renderData.getDetailNoiseScale(),
                renderData.getErosionNoiseScale(),
                renderData.getDensityMultiplier(),
                renderData.getCoverageMultiplier(),
                renderData.getHeightSquash(),
                renderData.getTowerStrength(),
                renderData.getAnvilStrength(),
                renderData.getPrecipitationCoreStrength(),
                renderData.getCloudSeed(),
                renderData.getDebugColorOrTint(),
                renderData.getMaterialProfile(),
                renderData.getShapeProfile(),
                renderData.getStormVisualTier(),
                renderData.getPrecipitationTier(),
                renderData.getShadowContribution(),
                renderData.getLightningInfluence()
        );
    }
}

package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;

/**
 * Minimal render-side view of one cloud cell for weather-map splatting and
 * funnel rendering. Producible from real synced CloudCells or, as a migration
 * fallback, from legacy CloudFieldSnapshots.
 */
public record VolumetricRenderCell(
        double x,
        double z,
        float baseY,
        float topY,
        float radiusMajor,
        float radiusMinor,
        float orientationRadians,
        float density,
        float edgeSoftness,
        float energy,
        float seed01,
        float funnelStrength,
        float funnelGroundY,
        float rotation
) {
    public static VolumetricRenderCell fromCell(CloudCell cell) {
        return new VolumetricRenderCell(
                cell.x(),
                cell.z(),
                cell.baseY(),
                cell.topY(),
                cell.radiusMajor(),
                cell.radiusMinor(),
                cell.orientationRadians(),
                cell.density(),
                cell.edgeSoftness(),
                cell.energy(),
                (cell.seed() & 0xFFFFL) / 65535.0F,
                cell.funnelStrength(),
                cell.funnelGroundY(),
                cell.rotation()
        );
    }

    public static VolumetricRenderCell fromFieldSnapshot(CloudFieldSnapshot snapshot) {
        float effectiveDensity = snapshot.effectiveDensity();
        float effectiveCoverage = snapshot.effectiveCoverage();
        return new VolumetricRenderCell(
                snapshot.center().x(),
                snapshot.center().z(),
                snapshot.baseY(),
                snapshot.topY(),
                Math.max(4.0F, snapshot.radius()),
                Math.max(4.0F, snapshot.radius() * 0.82F),
                (snapshot.seed() % 628L) * 0.01F,
                Math.min(1.0F, effectiveDensity * (0.55F + 0.45F * effectiveCoverage) * Math.max(0.4F, snapshot.hydrationProgress())),
                1.0F - effectiveCoverage * 0.6F,
                snapshot.stormPotential(),
                (snapshot.seed() & 0xFFFFL) / 65535.0F,
                0.0F,
                0.0F,
                0.0F
        );
    }
}

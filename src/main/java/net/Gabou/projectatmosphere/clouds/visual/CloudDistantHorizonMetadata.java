package net.Gabou.projectatmosphere.clouds.visual;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Compact LOD-ready metadata for future Distant Horizons integration.
 * This class intentionally has no DH dependency and performs no DH rendering.
 */
public record CloudDistantHorizonMetadata(
        UUID regionId,
        String dimensionId,
        Vec3 simplifiedPosition,
        float effectiveRadius,
        float effectiveBaseY,
        float effectiveTopY,
        CloudMorphologyFamily morphologyFamily,
        float visualImportance,
        float stormImportance,
        float shadowPotential,
        float lodPriority
) {
    public static CloudDistantHorizonMetadata from(CloudVisualState state) {
        return new CloudDistantHorizonMetadata(
                state.regionId(),
                state.dimensionId(),
                state.position(),
                state.effectiveRadius(),
                state.baseY(),
                state.topY(),
                state.morphologyFamily(),
                state.visibilityImportance(),
                state.stormStrength(),
                state.shadowPotential(),
                CloudVisualMetrics.lodPriority(state)
        );
    }
}

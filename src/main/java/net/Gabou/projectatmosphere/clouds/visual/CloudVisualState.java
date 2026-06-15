package net.Gabou.projectatmosphere.clouds.visual;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.modules.weather.PrecipitationTier;
import net.Gabou.projectatmosphere.modules.weather.StormVisualTier;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Read-only cloud visual metadata shared by future render, shader, shadow,
 * Distant Horizons, and long-distance storm systems.
 */
public record CloudVisualState(
        UUID regionId,
        UUID clusterId,
        String dimensionId,
        String cloudTypeId,
        CloudMorphologyFamily morphologyFamily,
        Vec3 position,
        Vec3 previousPosition,
        Vec3 velocity,
        float radius,
        float baseY,
        float topY,
        float density,
        float coverage,
        float cloudWater,
        float precipitationStrength,
        float stormStrength,
        float visualDarkness,
        float shadowPotential,
        float opacity,
        float verticalDevelopment,
        float visibilityImportance,
        StormVisualTier stormVisualTier,
        PrecipitationTier precipitationTier,
        int cloudSeed
) {
    public CloudVisualState {
        morphologyFamily = morphologyFamily == null ? CloudMorphologyFamily.PUFF : morphologyFamily;
        position = position == null ? Vec3.ZERO : position;
        previousPosition = previousPosition == null ? position : previousPosition;
        velocity = velocity == null ? Vec3.ZERO : velocity;
        stormVisualTier = stormVisualTier == null ? StormVisualTier.CLEAR : stormVisualTier;
        precipitationTier = precipitationTier == null ? PrecipitationTier.NONE : precipitationTier;
        radius = Math.max(0.0F, radius);
        topY = Math.max(baseY, topY);
        density = CloudVisualMetrics.clamp01(density);
        coverage = CloudVisualMetrics.clamp01(coverage);
        cloudWater = Math.max(0.0F, Math.min(1.2F, cloudWater));
        precipitationStrength = CloudVisualMetrics.clamp01(precipitationStrength);
        stormStrength = CloudVisualMetrics.clamp01(stormStrength);
        visualDarkness = CloudVisualMetrics.clamp01(visualDarkness);
        shadowPotential = CloudVisualMetrics.clamp01(shadowPotential);
        opacity = CloudVisualMetrics.clamp01(opacity);
        verticalDevelopment = CloudVisualMetrics.clamp01(verticalDevelopment);
        visibilityImportance = CloudVisualMetrics.clamp01(visibilityImportance);
    }

    public float effectiveRadius() {
        return radius * Math.max(0.35F, coverage);
    }

    public float effectiveHeight() {
        return Math.max(0.0F, topY - baseY);
    }

    public boolean isStormCandidate() {
        return stormStrength > 0.25F || CloudVisualMetrics.isStormMorphology(morphologyFamily);
    }

    public boolean isShadowCandidate() {
        return shadowPotential > 0.18F && opacity > 0.04F;
    }
}

package net.Gabou.projectatmosphere.clouds.visual;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;

/**
 * Converts existing read-only cloud render transport data into shared visual
 * metadata. It does not mutate cloud, weather, forecast, or atmosphere state.
 */
public final class CloudVisualStateFactory {
    private CloudVisualStateFactory() {
    }

    public static CloudVisualState fromRenderData(CloudRegionRenderData data) {
        if (data == null) {
            return null;
        }
        return fromRenderData(data, resolveCloudWater(data));
    }

    public static CloudVisualState fromRenderData(CloudRegionRenderData data, float cloudWater) {
        if (data == null) {
            return null;
        }
        return new CloudVisualState(
                data.getRegionId(),
                data.getClusterId(),
                data.getDimensionId(),
                data.getCloudTypeId(),
                data.getMorphologyFamily(),
                data.getCenter(),
                data.getPreviousCenter(),
                data.getVelocity(),
                data.getRadius(),
                data.getBaseY(),
                data.getTopY(),
                data.getDensity(),
                data.getCoverage(),
                cloudWater,
                CloudVisualMetrics.precipitationStrength(data),
                CloudVisualMetrics.stormStrength(data),
                CloudVisualMetrics.visualDarkness(data),
                CloudVisualMetrics.shadowPotential(data),
                CloudVisualMetrics.opacity(data),
                CloudVisualMetrics.verticalDevelopment(data),
                CloudVisualMetrics.longDistanceVisibilityImportance(data),
                data.getStormVisualTier(),
                data.getPrecipitationTier(),
                data.getCloudSeed()
        );
    }

    private static float resolveCloudWater(CloudRegionRenderData data) {
        if (data == null || data.getCenter() == null) {
            return 0.0F;
        }
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(RegionInstanceKey.from(BlockPos.containing(data.getCenter())));
        if (state != null) {
            return state.getCloudWater();
        }
        return CloudVisualMetrics.cloudWaterProxy(data);
    }
}

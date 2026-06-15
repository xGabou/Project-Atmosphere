package net.Gabou.projectatmosphere.clouds.visual;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.minecraft.util.Mth;

/**
 * Centralized render-facing cloud metric calculations.
 * This class does not mutate simulation state and does not call renderer APIs.
 */
public final class CloudVisualMetrics {
    private CloudVisualMetrics() {
    }

    public static float lifecycleFactor(CloudRegionRenderData data) {
        if (data == null) {
            return 0.0F;
        }
        return clamp01(data.getGrowth() * (1.0F - data.getDecay()));
    }

    public static float opacity(CloudRegionRenderData data) {
        if (data == null) {
            return 0.0F;
        }
        return clamp01(
                data.getDensity()
                        * data.getDensityMultiplier()
                        * data.getCoverage()
                        * data.getCoverageMultiplier()
                        * data.getMaterialProfile().getOpacityBias()
                        * lifecycleFactor(data)
        );
    }

    public static float precipitationStrength(CloudRegionRenderData data) {
        if (data == null) {
            return 0.0F;
        }
        float visualPrecipitation = data.getDensity()
                * data.getCoverage()
                * data.getPrecipitationCoreStrength()
                * lifecycleFactor(data);
        return clamp01(Math.max(visualPrecipitation, data.getPrecipitationTier().getRepresentativeIntensity()));
    }

    public static float stormStrength(CloudRegionRenderData data) {
        if (data == null) {
            return 0.0F;
        }
        return clamp01(
                data.getStormVisualTier().getDarkness() * 0.45F
                        + data.getStormVisualTier().getShadowBias() * 0.18F
                        + precipitationStrength(data) * 0.18F
                        + data.getTowerStrength() * 0.10F
                        + data.getAnvilStrength() * 0.09F
        );
    }

    public static float visualDarkness(CloudRegionRenderData data) {
        if (data == null) {
            return 0.0F;
        }
        float materialDarkness = Math.max(
                data.getMaterialProfile().getDarkness(),
                data.getMaterialProfile().getStormCoreDarkening()
        );
        return clamp01(
                materialDarkness * 0.42F
                        + data.getBaseDarkness() * 0.20F
                        + data.getStormVisualTier().getDarkness() * 0.24F
                        + precipitationStrength(data) * 0.14F
        );
    }

    public static float verticalDevelopment(CloudRegionRenderData data) {
        if (data == null) {
            return 0.0F;
        }
        float height = Math.max(0.0F, data.getTopY() - data.getBaseY());
        float heightScore = Mth.clamp(height / 192.0F, 0.0F, 1.0F);
        return clamp01(
                heightScore * 0.40F
                        + data.getVerticalThickness() * 0.16F
                        + data.getTowerStrength() * 0.24F
                        + data.getAnvilStrength() * 0.20F
        );
    }

    public static float shadowPotential(CloudRegionRenderData data) {
        if (data == null) {
            return 0.0F;
        }
        return clamp01(
                data.getShadowContribution() * 0.42F
                        + opacity(data) * 0.30F
                        + visualDarkness(data) * 0.18F
                        + data.getStormVisualTier().getShadowBias() * 0.10F
        );
    }

    public static float longDistanceVisibilityImportance(CloudRegionRenderData data) {
        if (data == null) {
            return 0.0F;
        }
        float sizeScore = Mth.clamp(data.getRadius() / 900.0F, 0.0F, 1.0F);
        return clamp01(
                sizeScore * 0.32F
                        + opacity(data) * 0.20F
                        + verticalDevelopment(data) * 0.18F
                        + stormStrength(data) * 0.18F
                        + shadowPotential(data) * 0.12F
        );
    }

    public static float lodPriority(CloudVisualState state) {
        if (state == null) {
            return 0.0F;
        }
        return clamp01(
                state.visibilityImportance() * 0.45F
                        + state.stormStrength() * 0.25F
                        + state.shadowPotential() * 0.15F
                        + Mth.clamp(state.effectiveRadius() / 1200.0F, 0.0F, 1.0F) * 0.15F
        );
    }

    public static float cloudWaterProxy(CloudRegionRenderData data) {
        if (data == null) {
            return 0.0F;
        }
        return Mth.clamp(
                data.getDensity() * data.getCoverage() * 0.35F
                        + data.getPrecipitationCoreStrength() * 0.35F
                        + precipitationStrength(data) * 0.30F,
                0.0F,
                1.2F
        );
    }

    public static boolean isStormMorphology(CloudMorphologyFamily family) {
        return family == CloudMorphologyFamily.TOWER || family == CloudMorphologyFamily.STORM_ANVIL;
    }

    static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }
}

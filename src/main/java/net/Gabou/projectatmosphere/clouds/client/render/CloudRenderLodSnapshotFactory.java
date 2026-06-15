package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.type.CloudMaterialProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.minecraft.util.Mth;

/**
 * Creates render-only LOD snapshots by reducing expensive visual detail while
 * preserving the source cloud identity, position, lifecycle, and weather data.
 */
final class CloudRenderLodSnapshotFactory {
    private CloudRenderLodSnapshotFactory() {
    }

    static CloudRenderSnapshot create(CloudRenderSnapshot source, CloudRenderLodManager.Candidate candidate) {
        CloudRenderLodTier tier = candidate.tier();
        float detail = resolveDetailScale(tier, candidate);
        float alpha = candidate.fadeAlpha();
        boolean majorStorm = CloudRenderLodManager.isMajorStorm(source);
        float stormVisibility = majorStorm ? 1.0F : 0.0F;

        float density = clamp01(source.getDensity() * Mth.lerp(stormVisibility * 0.45F, tier.getDensityScale(), 0.92F) * alpha);
        float coverage = clamp01(source.getCoverage() * Mth.lerp(stormVisibility * 0.20F, tier.getCoverageScale(), 1.0F));
        float radiusScale = Mth.lerp(1.0F - detail, 1.0F, majorStorm ? 1.08F : 1.03F);
        float edgeSoftness = clamp01(source.getEdgeSoftness() + (1.0F - detail) * (majorStorm ? 0.18F : 0.28F));

        return new CloudRenderSnapshot(
                source.isEnabled(),
                source.getDimension(),
                source.getWorldTime(),
                source.getPartialTick(),
                source.getCameraPosition(),
                source.getRegionCenter(),
                source.getPreviousRegionCenter(),
                source.getVelocity(),
                source.getRegionRadius() * radiusScale,
                source.getCloudBaseY(),
                source.getCloudTopY(),
                density,
                coverage,
                edgeSoftness,
                source.getWindOffsetX(),
                source.getWindOffsetZ(),
                source.getAgeTicks(),
                source.getLifetimeTicks(),
                source.getGrowth(),
                source.getDecay(),
                source.getCloudTypeId(),
                source.getPreviousCloudTypeId(),
                source.getMorphologyFamily(),
                source.getCloudTypeTicks(),
                source.getVerticalThickness(),
                source.getEdgeErosionStrength() * Mth.lerp(detail, 0.30F, 1.0F),
                source.getTopSoftness() + (1.0F - detail) * 0.10F,
                source.getBaseSoftness() + (1.0F - detail) * 0.08F,
                source.getBaseDarkness(),
                source.getNoiseScale() / Mth.lerp(detail, 2.20F, 1.0F),
                Math.max(0.001F, source.getDetailNoiseScale() * Mth.lerp(detail, 0.18F, 1.0F)),
                Math.max(0.001F, source.getErosionNoiseScale() * Mth.lerp(detail, 0.22F, 1.0F)),
                source.getDensityMultiplier(),
                source.getCoverageMultiplier(),
                source.getHeightSquash(),
                source.getTowerStrength() * Mth.lerp(detail, 0.70F, 1.0F),
                source.getAnvilStrength() * Mth.lerp(detail, 0.82F, 1.0F),
                source.getPrecipitationCoreStrength(),
                source.getCloudSeed(),
                source.getDebugColorOrTint(),
                reduceMaterial(source.getMaterialProfile(), detail, alpha),
                reduceShape(source.getShapeProfile(), detail, majorStorm),
                source.getStormVisualTier(),
                source.getPrecipitationTier(),
                source.getShadowContribution(),
                source.getLightningInfluence()
        );
    }

    private static float resolveDetailScale(CloudRenderLodTier tier, CloudRenderLodManager.Candidate candidate) {
        return Mth.clamp(tier.getDetailScale() * Mth.lerp(candidate.detailBlend(), 0.72F, 1.0F), 0.05F, 1.0F);
    }

    private static CloudMaterialProfile reduceMaterial(CloudMaterialProfile source, float detail, float alpha) {
        if (source == null) {
            source = CloudMaterialProfile.DEFAULT;
        }
        return new CloudMaterialProfile(
                source.getMaterialId(),
                source.getTextureId(),
                source.getDarkness(),
                source.getPrecipitationTint(),
                source.getOpacityBias() * Mth.lerp(detail, 0.74F, 1.0F) * Mth.clamp(alpha + 0.25F, 0.0F, 1.0F),
                source.getUndersideDarkness(),
                source.getEdgeErosion() * Mth.lerp(detail, 0.28F, 1.0F),
                source.getStormCoreDarkening(),
                source.getShadowContribution(),
                source.getLightningResponse()
        );
    }

    private static CloudShapeProfile reduceShape(CloudShapeProfile source, float detail, boolean majorStorm) {
        if (source == null) {
            source = CloudShapeProfile.DEFAULT;
        }
        float preservedStormDetail = majorStorm ? 0.35F : 0.0F;
        float shapeDetail = Math.max(detail, preservedStormDetail);
        int minLobes = Math.max(1, Math.round(source.getLobeCountMin() * Mth.lerp(shapeDetail, 0.28F, 1.0F)));
        int maxLobes = Math.max(minLobes, Math.round(source.getLobeCountMax() * Mth.lerp(shapeDetail, 0.24F, 1.0F)));
        return new CloudShapeProfile(
                source.getShapeId(),
                source.getBaseRadius(),
                source.getBaseOffset(),
                source.getTopOffset(),
                minLobes,
                maxLobes,
                source.getLobeStrength() * Mth.lerp(shapeDetail, 0.38F, 1.0F),
                source.getVerticalTilt() * Mth.lerp(shapeDetail, 0.40F, 1.0F),
                source.getWindShearStrength() * Mth.lerp(shapeDetail, 0.36F, 1.0F),
                source.getCellSplitStrength() * Mth.lerp(shapeDetail, 0.18F, 1.0F),
                source.getTowerNarrowing(),
                source.getAnvilSpread(),
                source.getBaseFlattening(),
                source.getEdgeRaggedness() * Mth.lerp(shapeDetail, 0.22F, 1.0F),
                source.getStormWallStrength()
        );
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }
}

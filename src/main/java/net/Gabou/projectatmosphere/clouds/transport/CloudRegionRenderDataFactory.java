package net.Gabou.projectatmosphere.clouds.transport;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudVisualProfile;
import net.Gabou.projectatmosphere.modules.weather.PrecipitationTier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Convertit l'état backend d'une région de nuage en donnée de rendu transportable.
 * Cette classe ne lit aucune classe de rendu client et ne crée aucun snapshot client.
 */
public final class CloudRegionRenderDataFactory {

    private static final float DEFAULT_DENSITY = 0.65F;
    private static final float DEFAULT_COVERAGE = 0.75F;
    private static final float DEFAULT_EDGE_SOFTNESS = 0.35F;
    private static final int DEFAULT_DEBUG_COLOR = 0x99FFFFFF;

    private CloudRegionRenderDataFactory() {

    }

    /**
     * Crée une donnée de rendu debug transportable depuis un état backend.
     *
     * @param state état backend de région de nuage
     * @return donnée de rendu de nuage transportable
     */
    public static @NotNull CloudRegionRenderData createDebug(@NotNull CloudRegionState state) {
        CloudClusterState cluster = selectRenderableCluster(state);
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cluster.getCloudTypeId());
        CloudVisualProfile profile = definition.getVisualProfile();
        CloudShapeProfile shapeProfile = definition.getShapeProfile();

        return createWithProfile(
            state,
                cluster,
                DEFAULT_DENSITY,
                DEFAULT_COVERAGE,
                DEFAULT_EDGE_SOFTNESS,
                DEFAULT_DEBUG_COLOR,
                definition,
                profile,
                shapeProfile
        );
    }

    /**
     * Crée une donnée de rendu transportable depuis un état backend.
     *
     * @param state état backend de région de nuage
     * @return donnée de rendu de nuage transportable
     */
    public static @NotNull CloudRegionRenderData create(@NotNull CloudRegionState state) {
        CloudClusterState cluster = selectRenderableCluster(state);
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cluster.getCloudTypeId());
        CloudTypeDefinition previousDefinition = CloudTypeRegistry.getOrDefault(cluster.getPreviousCloudTypeId());
        CloudVisualProfile profile = CloudVisualProfile.blend(
                previousDefinition.getVisualProfile(),
                definition.getVisualProfile(),
                Mth.clamp(cluster.getTransitionBlend() + (cluster.getMergePressure() * 0.35F), 0.0F, 1.0F)
        );
        CloudShapeProfile shapeProfile = CloudShapeProfile.blend(
                previousDefinition.getShapeProfile(),
                definition.getShapeProfile(),
                Mth.clamp(cluster.getTransitionBlend() + (cluster.getMergePressure() * 0.35F), 0.0F, 1.0F)
        );

        return createWithProfile(
                state,
                cluster,
                cluster.getDensity(),
                cluster.getCoverage(),
                cluster.getEdgeSoftness(),
                0xFFFFFFFF,
                definition,
                profile,
                shapeProfile
        );
    }

    public static @NotNull CloudRegionRenderData createForCluster(
            @NotNull CloudRegionState region,
            @NotNull CloudClusterState cluster
    ) {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cluster.getCloudTypeId());
        CloudTypeDefinition previousDefinition = CloudTypeRegistry.getOrDefault(cluster.getPreviousCloudTypeId());
        CloudVisualProfile profile = CloudVisualProfile.blend(
                previousDefinition.getVisualProfile(),
                definition.getVisualProfile(),
                Mth.clamp(cluster.getTransitionBlend() + (cluster.getMergePressure() * 0.35F), 0.0F, 1.0F)
        );
        CloudShapeProfile shapeProfile = CloudShapeProfile.blend(
                previousDefinition.getShapeProfile(),
                definition.getShapeProfile(),
                Mth.clamp(cluster.getTransitionBlend() + (cluster.getMergePressure() * 0.35F), 0.0F, 1.0F)
        );

        return createWithProfile(
                region,
                cluster,
                cluster.getDensity(),
                cluster.getCoverage(),
                cluster.getEdgeSoftness(),
                0xFFFFFFFF,
                definition,
                profile,
                shapeProfile
        );
    }

    private static @NotNull CloudRegionRenderData createWithProfile(
            @NotNull CloudRegionState state,
            @NotNull CloudClusterState cluster,
            float density,
            float coverage,
            float edgeSoftness,
            int debugColorOrTint,
            @NotNull CloudTypeDefinition definition,
            @NotNull CloudVisualProfile profile,
            @NotNull CloudShapeProfile shapeProfile
    ) {
        float mergePressure = cluster.getMergePressure();
        float mergeScale = 1.0F + (mergePressure * 0.08F);
        float finalDensity = clamp01(density * (1.0F - (mergePressure * 0.05F)));
        float finalCoverage = clamp01(coverage * (1.0F + (mergePressure * 0.07F)));
        float finalEdgeSoftness = clamp01(edgeSoftness + (mergePressure * 0.16F));
        float finalRadius = Math.max(1.0F, cluster.getRadius() * mergeScale);
        float finalBaseY = cluster.getBaseY() - (mergePressure * 2.5F);
        float finalTopY = cluster.getTopY() + (mergePressure * 6.5F);
        PrecipitationTier precipitationTier = resolvePrecipitationTier(definition, profile, finalDensity, finalCoverage);
        float stormDarkening = Math.max(definition.getStormVisualTier().getDarkness(), definition.getMaterialProfile().getStormCoreDarkening());
        float shadowContribution = clamp01(
                definition.getMaterialProfile().getShadowContribution()
                        * (0.55F + finalDensity * 0.30F + finalCoverage * 0.25F)
                        + stormDarkening * 0.20F
        );
        float lightningInfluence = CloudTypeRegistry.isThunderCloud(definition.getId())
                ? Math.max(0.35F, definition.getMaterialProfile().getLightningResponse() * 0.45F)
                : 0.0F;

        return new CloudRegionRenderData(
                state.getRegionId(),
                cluster.getClusterId(),
                state.getDimension().location().toString(),
                cluster.getCenter(),
                cluster.getPreviousCenter(),
                cluster.getVelocity(),
                finalRadius,
                finalBaseY,
                finalTopY,
                finalDensity,
                finalCoverage,
                finalEdgeSoftness,
                state.isActive(),
                debugColorOrTint,
                state.getAgeTicks(),
                state.getLifetimeTicks(),
                state.getGrowth(),
                state.getDecay(),
                definition.getId(),
                state.getPreviousCloudTypeId(),
                cluster.getMorphologyFamily(),
                state.getCloudTypeTicks(),
                profile.getVerticalThickness(),
                profile.getEdgeErosionStrength(),
                profile.getTopSoftness(),
                profile.getBaseSoftness(),
                profile.getBaseDarkness(),
                profile.getNoiseScale(),
                profile.getDetailNoiseScale(),
                profile.getErosionNoiseScale(),
                profile.getDensityMultiplier(),
                profile.getCoverageMultiplier(),
                profile.getHeightSquash(),
                profile.getTowerStrength(),
                profile.getAnvilStrength(),
                profile.getPrecipitationCoreStrength(),
                cluster.getCloudSeed(),
                mergePressure,
                definition.getMaterialProfile().withVisualDefaults(profile),
                shapeProfile,
                definition.getStormVisualTier(),
                precipitationTier,
                shadowContribution,
                lightningInfluence
        );
    }

    private static @NotNull CloudClusterState selectRenderableCluster(@NotNull CloudRegionState state) {
        CloudClusterState dominant = null;
        for (CloudClusterState cluster : state.getClusters()) {
            if (cluster == null || !cluster.isActive()) {
                continue;
            }
            if (dominant == null
                    || cluster.getFootprint() > dominant.getFootprint()
                    || (cluster.getFootprint() == dominant.getFootprint() && cluster.getClusterId().compareTo(dominant.getClusterId()) < 0)) {
                dominant = cluster;
            }
        }

        if (dominant != null) {
            return dominant;
        }

        CloudClusterState fallback = new CloudClusterState(
                java.util.UUID.randomUUID(),
                state.getDimension(),
                state.getCenter(),
                Math.max(1.0F, state.getRadius()),
                Math.max(0.0F, state.getBaseY()),
                Math.max(state.getBaseY() + 1.0F, state.getTopY())
        );
        fallback.setDensity(Math.max(DEFAULT_DENSITY, state.getDensity()));
        fallback.setCoverage(Math.max(DEFAULT_COVERAGE, state.getCoverage()));
        fallback.setEdgeSoftness(Math.max(DEFAULT_EDGE_SOFTNESS, state.getEdgeSoftness()));
        fallback.setCloudTypeId(state.getCloudTypeId());
        fallback.setPreviousCloudTypeId(state.getPreviousCloudTypeId());
        fallback.setCloudTypeTicks(state.getCloudTypeTicks());
        fallback.setCloudSeed(state.getCloudSeed());
        fallback.setMergePressure(state.getMergePressure());
        return fallback;
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static PrecipitationTier resolvePrecipitationTier(
            @NotNull CloudTypeDefinition definition,
            @NotNull CloudVisualProfile profile,
            float density,
            float coverage
    ) {
        if (!CloudTypeRegistry.isPrecipitatingCloud(definition.getId())) {
            return PrecipitationTier.NONE;
        }

        float rainIntensity = density * 0.35F
                + coverage * 0.30F
                + profile.getPrecipitationCoreStrength() * 0.45F
                + definition.getStormVisualTier().getDarkness() * 0.25F;
        return PrecipitationTier.fromRainIntensity(rainIntensity);
    }
}

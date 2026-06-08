package net.Gabou.projectatmosphere.clouds.transport;

import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.clouds.type.CloudVisualProfile;
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
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(state.getCloudTypeId());
        CloudVisualProfile profile = definition.getVisualProfile();

        return createWithProfile(
                state,
                DEFAULT_DENSITY,
                DEFAULT_COVERAGE,
                DEFAULT_EDGE_SOFTNESS,
                DEFAULT_DEBUG_COLOR,
                definition,
                profile
        );
    }

    /**
     * Crée une donnée de rendu transportable depuis un état backend.
     *
     * @param state état backend de région de nuage
     * @return donnée de rendu de nuage transportable
     */
    public static @NotNull CloudRegionRenderData create(@NotNull CloudRegionState state) {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(state.getCloudTypeId());
        CloudVisualProfile profile = definition.getVisualProfile();

        return createWithProfile(
                state,
                state.getDensity(),
                state.getCoverage(),
                state.getEdgeSoftness(),
                0xFFFFFFFF,
                definition,
                profile
        );
    }

    private static @NotNull CloudRegionRenderData createWithProfile(
            @NotNull CloudRegionState state,
            float density,
            float coverage,
            float edgeSoftness,
            int debugColorOrTint,
            @NotNull CloudTypeDefinition definition,
            @NotNull CloudVisualProfile profile
    ) {
        return new CloudRegionRenderData(
                state.getRegionId(),
                state.getDimension().location().toString(),
                state.getCenter(),
                state.getPreviousCenter(),
                state.getVelocity(),
                state.getRadius(),
                state.getBaseY(),
                state.getTopY(),
                density,
                coverage,
                edgeSoftness,
                state.isActive(),
                debugColorOrTint,
                state.getAgeTicks(),
                state.getLifetimeTicks(),
                state.getGrowth(),
                state.getDecay(),
                definition.getId(),
                state.getPreviousCloudTypeId(),
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
                profile.getPrecipitationCoreStrength()
        );
    }
}

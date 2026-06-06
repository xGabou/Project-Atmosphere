package net.Gabou.projectatmosphere.clouds.backend;

import org.jetbrains.annotations.NotNull;

/**
 * Convertit l'état backend d'une région de nuage en donnée de rendu transportable.
 * Cette classe ne lit aucune classe de rendu client et ne crée aucun snapshot client.
 */
final class CloudRegionRenderDataFactory {

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
    static @NotNull CloudRegionRenderData createDebug(@NotNull CloudRegionState state) {
        return new CloudRegionRenderData(
                state.getRegionId(),
                state.getDimension().location().toString(),
                state.getCenter(),
                state.getPreviousCenter(),
                state.getVelocity(),
                state.getRadius(),
                state.getBaseY(),
                state.getTopY(),
                DEFAULT_DENSITY,
                DEFAULT_COVERAGE,
                DEFAULT_EDGE_SOFTNESS,
                state.isActive(),
                DEFAULT_DEBUG_COLOR,
                state.getAgeTicks(),
                state.getLifetimeTicks(),
                state.getGrowth(),
                state.getDecay()
        );
    }

    /**
     * Crée une donnée de rendu transportable depuis un état backend.
     *
     * @param state état backend de région de nuage
     * @return donnée de rendu de nuage transportable
     */
    static @NotNull CloudRegionRenderData create(@NotNull CloudRegionState state) {
        return new CloudRegionRenderData(
                state.getRegionId(),
                state.getDimension().location().toString(),
                state.getCenter(),
                state.getPreviousCenter(),
                state.getVelocity(),
                state.getRadius(),
                state.getBaseY(),
                state.getTopY(),
                state.getDensity(),
                state.getCoverage(),
                state.getEdgeSoftness(),
                state.isActive(),
                0xFFFFFFFF,
                state.getAgeTicks(),
                state.getLifetimeTicks(),
                state.getGrowth(),
                state.getDecay()
        );
    }
}

package net.Gabou.projectatmosphere.clouds.backend;

import org.jetbrains.annotations.NotNull;

/**
 * Converts backend cloud region state into transport safe cloud render data.
 * This class does not read client render classes and does not create client snapshots.
 */
public final class CloudRegionRenderDataFactory {

    private static final float DEFAULT_DENSITY = 0.65F;
    private static final float DEFAULT_COVERAGE = 0.75F;
    private static final float DEFAULT_EDGE_SOFTNESS = 0.35F;
    private static final int DEFAULT_DEBUG_COLOR = 0x99FFFFFF;

    private CloudRegionRenderDataFactory() {

    }

    /**
     * Creates transport safe render data from a backend cloud region state.
     *
     * @param state backend cloud region state
     * @return transport safe cloud render data
     */
    public static @NotNull CloudRegionRenderData createDebug(@NotNull CloudRegionState state) {
        return new CloudRegionRenderData(
                state.getRegionId(),
                state.getDimension().location().toString(),
                state.getCenter(),
                state.getRadius(),
                state.getBaseY(),
                state.getTopY(),
                DEFAULT_DENSITY,
                DEFAULT_COVERAGE,
                DEFAULT_EDGE_SOFTNESS,
                state.isActive(),
                DEFAULT_DEBUG_COLOR
        );
    }
}
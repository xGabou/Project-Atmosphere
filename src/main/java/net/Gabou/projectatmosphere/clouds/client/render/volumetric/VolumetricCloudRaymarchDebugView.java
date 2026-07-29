package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import org.jetbrains.annotations.NotNull;

/** Selects which temporal-raymarch stage is written to the cloud target. */
public enum VolumetricCloudRaymarchDebugView {
    FINAL(0, "final"),
    CURRENT_ONLY(1, "current"),
    HISTORY_ONLY(2, "history"),
    HISTORY_REJECTION(3, "history_rejection"),
    HISTORY_DEPTH_SPACE(4, "history_depth_space"),
    LIGHTING_COMPONENTS(5, "lighting_components"),
    LIGHT_MARCH_PHASE(6, "light_march_phase"),
    LIGHT_MARCH_CAP(7, "light_march_cap"),
    LIGHT_MARCH_REFINED(8, "light_march_refined"),
    LIGHT_MARCH_DETAIL(9, "light_march_detail"),
    PUFF_LOCAL_HEIGHT(10, "puff_local_height"),
    PRIMARY_QUADRATURE(11, "primary_quadrature"),
    FINE_STEP_QUADRATURE(12, "fine_step_quadrature"),
    DRY_BASE_RAIN(13, "dry_base_rain"),
    MISSED_FINE_MATERIAL(14, "missed_fine_material"),
    ACCEPTED_FINE_QUADRATURE(15, "accepted_fine_quadrature"),
    FINE_STEP_MIDPOINT(16, "fine_step_midpoint"),
    FINE_STEP_ALPHA(17, "fine_step_alpha"),
    FINE_DENSITY_QUADRATURE(18, "fine_density_quadrature"),
    FINE_LIGHTING_QUADRATURE(19, "fine_lighting_quadrature"),
    FINE_WEIGHTED_SOURCE(20, "fine_weighted_source");

    private final int shaderId;
    private final String serializedName;

    VolumetricCloudRaymarchDebugView(int shaderId, @NotNull String serializedName) {
        this.shaderId = shaderId;
        this.serializedName = serializedName;
    }

    public int shaderId() {
        return shaderId;
    }

    public @NotNull String serializedName() {
        return serializedName;
    }
}

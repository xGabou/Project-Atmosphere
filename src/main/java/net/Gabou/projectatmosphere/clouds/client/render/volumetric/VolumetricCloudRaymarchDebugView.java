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
    FINE_WEIGHTED_SOURCE(20, "fine_weighted_source"),
    /** On-demand four-pass native-storm material trace; never a production view. */
    STORM_MATERIAL_TRACE(21, "storm_material_trace"),
    /** On-demand workload channels: primary steps, descriptor evals/fetches, T122 avoided fetches. */
    STORM_WORKLOAD_PRIMARY(22, "storm_workload_primary"),
    /** On-demand workload channels: light evals, empty rejects/exits, T121 conservative skips. */
    STORM_WORKLOAD_SECONDARY(23, "storm_workload_secondary"),
    /** T141 channels: storm-shape calls, group-field calls, lobes visited, density calls. */
    STORM_WORKLOAD_TERTIARY(24, "storm_workload_tertiary"),
    /** T141 channels: zero-density calls, segment tests, positive segment tests, box rejects. */
    STORM_WORKLOAD_QUATERNARY(25, "storm_workload_quaternary"),
    /** T149 channels: executed packed detail-noise octave evaluations. */
    STORM_WORKLOAD_QUINARY(26, "storm_workload_quinary"),
    /** T153 untimed ground-truth interval publication pass. */
    T153_ORACLE_GROUND_TRUTH(27, "t153_oracle_ground_truth"),
    /** T153 skipped-distance attribution: total, pre-cloud, holes, post-cloud. */
    STORM_WORKLOAD_ORACLE_DISTANCE(28, "storm_workload_oracle_distance"),
    /** T153 oracle events, interval count, overflow pixels and optical exits. */
    STORM_WORKLOAD_ORACLE_STATUS(29, "storm_workload_oracle_status"),
    /** T153 primary steps after alpha 50/90/95/98 percent. */
    STORM_WORKLOAD_ORACLE_ALPHA_STEPS(30, "storm_workload_oracle_alpha_steps"),
    /** T153 cloud-density calls after alpha 50/90/95/98 percent. */
    STORM_WORKLOAD_ORACLE_ALPHA_DENSITY(31, "storm_workload_oracle_alpha_density"),
    /** T153 descriptor evaluations after alpha 50/90/95/98 percent. */
    STORM_WORKLOAD_ORACLE_ALPHA_DESCRIPTOR(32, "storm_workload_oracle_alpha_descriptor"),
    /** T153 lighting evaluations after alpha 50/90/95/98 percent. */
    STORM_WORKLOAD_ORACLE_ALPHA_LIGHT(33, "storm_workload_oracle_alpha_light"),
    /** T153 detail-octave evaluations after alpha 50/90/95/98 percent. */
    STORM_WORKLOAD_ORACLE_ALPHA_DETAIL(34, "storm_workload_oracle_alpha_detail");

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

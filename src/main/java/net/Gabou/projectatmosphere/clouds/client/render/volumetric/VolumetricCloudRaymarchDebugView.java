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
    STORM_WORKLOAD_ORACLE_ALPHA_DETAIL(34, "storm_workload_oracle_alpha_detail"),
    /** T169 lighting attribution: cone marches, cone taps, cone early-outs, cheap probes. */
    STORM_WORKLOAD_LIGHT_ATTRIBUTION(35, "storm_workload_light_attribution"),
    /** T169 detail attribution: primary fetches, light fetches, second octave, opaque marches. */
    STORM_WORKLOAD_DETAIL_ATTRIBUTION(36, "storm_workload_detail_attribution"),
    /**
     * T175 primary density histogram, part 1: calls, exactly zero, negligible,
     * low. Primary body samples only - the light march is excluded by
     * construction, since these are counted at the single body call site.
     */
    STORM_WORKLOAD_PRIMARY_DENSITY_A(37, "storm_workload_primary_density_a"),
    /** T175 primary density histogram, part 2: medium, high, material runs, zero runs. */
    STORM_WORKLOAD_PRIMARY_DENSITY_B(38, "storm_workload_primary_density_b"),

    /**
     * T177. Primary-to-light group reuse validity: how often the group a light
     * tap actually needs was already resolved by the primary sample that
     * spawned the cone march.
     */
    STORM_WORKLOAD_REUSE_A(39, "storm_workload_reuse_a"),

    /** T177. Reuse misses, groups entered inside taps, and ordinals 1-2. */
    STORM_WORKLOAD_REUSE_B(40, "storm_workload_reuse_b"),

    /** T177. Reuse validity for tap ordinals 3-4. */
    STORM_WORKLOAD_REUSE_C(41, "storm_workload_reuse_c"),

    /**
     * T178. Separates a lobe visit from an expensive lobe evaluation: exact
     * SDFs, exact SDFs that did not move the union, and the light march's share
     * of both.
     */
    STORM_WORKLOAD_LOBE_A(42, "storm_workload_lobe_a"),

    /** T178. Light-attributed cheap rejects and support-bound rejects. */
    STORM_WORKLOAD_LOBE_B(43, "storm_workload_lobe_b"),

    /**
     * T179. The dominance histogram: what each exact SDF actually did to the
     * accumulated union, binned by magnitude of change.
     */
    STORM_WORKLOAD_DOMINANCE_A(44, "storm_workload_dominance_a"),

    /** T179. Zero-change SDFs by consumer, and the slack in the T121 bound. */
    STORM_WORKLOAD_DOMINANCE_B(45, "storm_workload_dominance_b"),

    /**
     * T180. Direct attribution of the descriptor walk to its real production
     * consumers, replacing T175's estimate by subtraction.
     */
    STORM_WORKLOAD_CONSUMER_A(46, "storm_workload_consumer_a"),

    /** T180. Bracket bisection and unattributed consumers. */
    STORM_WORKLOAD_CONSUMER_B(47, "storm_workload_consumer_b"),

    /** T180. Exact SDFs charged to unattributed consumers. */
    STORM_WORKLOAD_CONSUMER_C(48, "storm_workload_consumer_c"),

    /** T181. Refinement events, scan events, material hits and cap hits. */
    STORM_WORKLOAD_SCAN_A(49, "storm_workload_scan_a"),

    /** T181. How many probes each scan actually consumed. */
    STORM_WORKLOAD_SCAN_B(50, "storm_workload_scan_b"),

    /** T181. Probes spent in scans that then re-marched the same lattice. */
    STORM_WORKLOAD_SCAN_C(51, "storm_workload_scan_c"),

    /** T182. directStormShape calls by consumer: primary, light, probe, bracket. */
    STORM_WORKLOAD_SHAPE_A(52, "storm_workload_shape_a"),

    /** T182. Refinement, rain segment, rain shaft and camera-inside. */
    STORM_WORKLOAD_SHAPE_B(53, "storm_workload_shape_b"),

    /** T182. Light forward probe, and the untagged residual that must be zero. */
    STORM_WORKLOAD_SHAPE_C(54, "storm_workload_shape_c"),

    /** T184. Rain-support calls, T145 prunes, and exact/block reuse hits. */
    STORM_WORKLOAD_RAIN_A(55, "storm_workload_rain_a"),

    /** T184. Rain-support reuse at weather-tile granularity. */
    STORM_WORKLOAD_RAIN_B(56, "storm_workload_rain_b"),

    /** T185. Each half of the T145 conjunct, plus tighter-box and exact tests. */
    STORM_WORKLOAD_RAIN_C(57, "storm_workload_rain_c"),

    /** T185. Columns the prune accepted that carried no support anyway. */
    STORM_WORKLOAD_RAIN_D(58, "storm_workload_rain_d"),

    /** T186. Segment-test invocations and the per-sample support split. */
    STORM_WORKLOAD_RAIN_E(59, "storm_workload_rain_e"),

    /** T186. Which sample actually found the rain. */
    STORM_WORKLOAD_RAIN_F(60, "storm_workload_rain_f"),

    /** T188. Rain-field fetches, and the out-of-domain columns that still walk. */
    STORM_WORKLOAD_RAIN_FIELD_A(61, "storm_workload_rain_field_a"),

    /**
     * T188. The descriptor work ONE field cell costs to build, so the
     * generation pass can be priced with the same counters the ray uses.
     */
    STORM_WORKLOAD_RAIN_FIELD_B(62, "storm_workload_rain_field_b"),

    /**
     * T189. The field's error against the exact function: support delta,
     * attach-height delta where both paths agree on ownership, ownership
     * disagreements, and the count of columns both own.
     */
    STORM_WORKLOAD_RAIN_FIELD_C(63, "storm_workload_rain_field_c"),

    /**
     * T189. Whether the field texture is filtered at all: fractional ownership
     * samples, and the filtered-minus-exact delta on the ownership and support
     * channels. Tests the premise T188's diagnosis rested on.
     */
    STORM_WORKLOAD_RAIN_FIELD_D(64, "storm_workload_rain_field_d"),

    /** T190. Why a column disagrees: ownership, support cutoff, or attach height. */
    STORM_WORKLOAD_RAIN_FIELD_E(65, "storm_workload_rain_field_e"),

    /** T190. The mixed-cell census, sampled off the field the build wrote. */
    STORM_WORKLOAD_RAIN_FIELD_F(66, "storm_workload_rain_field_f");

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

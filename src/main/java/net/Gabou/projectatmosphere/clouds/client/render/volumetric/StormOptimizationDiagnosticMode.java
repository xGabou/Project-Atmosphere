package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.Locale;

/**
 * T133 / SC-020 diagnostic selector for the storm performance optimizations
 * that have no natural runtime toggle.
 *
 * <p>{@link #NORMAL_PRODUCTION} is the only value the renderer uploads outside a
 * deliberate diagnostic capture, and it is the reset default, so ordinary
 * frames take exactly the optimized paths they took before this selector
 * existed. The OFF arms exist to prove, against an unoptimized but
 * mathematically identical execution, that the production ON paths preserve the
 * image. They change no equation, no sample position, no jitter sequence and no
 * texture content - only whether the optimization is used.
 *
 * <p>The shader flags are a bit set so a later task can request a combination
 * without another uniform; the suite only ever selects one at a time.
 */
public enum StormOptimizationDiagnosticMode {
    /** Production: T121 rejection and T122 fetch reuse both active. */
    NORMAL_PRODUCTION(0, "normal_production"),
    /** T121's conservative descriptor rejection bypassed. */
    T121_OFF(1, "t121_off"),
    /** T122's descriptor-fetch reuse bypassed; the texels are refetched. */
    T122_OFF(2, "t122_off"),
    /**
     * T141: each lobe's exact SDF is evaluated twice on the texels already in
     * registers. Descriptor evaluations double while descriptor texel fetches
     * are unchanged, so the GPU-time delta is the marginal cost of descriptor
     * evaluation - the quantity T122's fetch arm never varied. The second
     * evaluation is perturbed by a uniform uploaded as exactly zero, so the
     * result is bit-identical and the image cannot change.
     */
    T141_EVAL_AMPLIFY(4, "t141_eval_amplify"),
    /**
     * T141: T121's conservative rejection uses a horizontal-and-vertical lower
     * bound instead of the vertical-only one. The comparison is unchanged, and
     * the maximum of two valid lower bounds is a valid lower bound, so the arm
     * can only reject more lobes - never different ones.
     */
    T141_BOX_BOUND(8, "t141_box_bound"),
    /**
     * T143: storm reachability is hoisted out of the per-step march loop. One
     * ray-invariant horizontal bound over every resident descriptor is built
     * once per fragment, and a column outside it skips the candidate walk, the
     * group unions, the per-descriptor segment test and the all-descriptor base
     * loop that the rain probe runs twice per step. The bound is a superset of
     * every reach the shader tests against elsewhere and the clearance it
     * publishes is a genuine lower bound, so no material can be skipped.
     */
    T143_REACHABILITY(16, "t143_reachability"),
    /**
     * T145's precipitation-locality gate bypassed, restoring the unguarded rain
     * probe. The gate is production behaviour: the per-step rain probe may not
     * enter descriptor traversal until locality is decided. A probe at or above an upper bound on
     * the attachment height cannot contribute, and a column outside the union
     * of the descriptors' ownership ellipses cannot be descriptor-owned - so
     * when the raster precipitation is also absent the probe's own early-out is
     * already decided. Both conditions are conservative and neither changes a
     * density, a rain equation or the march's progression.
     */
    T145_OFF(32, "t145_off"),
    /**
     * T147: the march's far endpoint is halved. This is not a distance policy,
     * it is the ceiling of one - it deletes every sample beyond half the render
     * distance outright, so whatever it saves bounds what any cheapening of
     * distant work could return.
     */
    T147_HALF_DISTANCE(64, "t147_half_distance"),
    /**
     * T147: the detail-noise octaves are dropped everywhere. Ceiling of a
     * detail LOD, in the same sense.
     */
    T147_DETAIL_OFF(128, "t147_detail_off"),
    /** T149: light cone graded by how much the sample can still contribute. */
    T149_LIGHT_CONTRIBUTION(256, "t149_light_contribution"),
    /** T149: light cone graded by distance. */
    T149_LIGHT_DISTANCE(512, "t149_light_distance"),
    /** T149: detail is simplified only when its projected/contribution weight is small. */
    T149_DETAIL_GRADED(1024, "t149_detail_graded"),
    /** T149: light cone graded by continuous ray verticality. */
    T149_LIGHT_VERTICAL(2048, "t149_light_vertical"),
    /** T149: all continuous lighting signals, whichever is most restrictive. */
    T149_LIGHT_GRADED(2816, "t149_light_graded"),
    /** T149 candidate: the complete graded lighting and detail policy. */
    T149_GRADED(3840, "t149_graded"),
    /** T153 oracle: omit expensive density work at ground-truth-empty samples. */
    T153_PERFECT_EMPTY_SKIP(4096, "t153_perfect_empty_skip"),
    /** T153 oracle: jump directly between ground-truth occupied intervals. */
    T153_PERFECT_OCCUPIED_INTERVALS(8192, "t153_perfect_occupied_intervals"),
    /** T153 oracle: stop once the diagnostic optical-relevance limit is reached. */
    T153_PERFECT_OPTICAL_RELEVANCE(16384, "t153_perfect_optical_relevance"),
    /** T153 oracle: occupied-interval traversal plus optical relevance. */
    T153_COMBINED(28672, "t153_combined");

    private final int shaderFlags;
    private final String serializedName;

    StormOptimizationDiagnosticMode(int shaderFlags, String serializedName) {
        this.shaderFlags = shaderFlags;
        this.serializedName = serializedName;
    }

    public int shaderFlags() {
        return shaderFlags;
    }

    public String serializedName() {
        return serializedName;
    }

    /** True for any mode that disables a production optimization. */
    public boolean diagnostic() {
        return shaderFlags != 0;
    }

    /** True only for the T153 ceiling experiment's diagnostic replay arms. */
    public boolean t153Oracle() {
        return (shaderFlags & 28672) != 0;
    }

    public static StormOptimizationDiagnosticMode parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (StormOptimizationDiagnosticMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        return null;
    }
}

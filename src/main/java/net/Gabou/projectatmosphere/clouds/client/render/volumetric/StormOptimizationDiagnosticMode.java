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
    T141_BOX_BOUND(8, "t141_box_bound");

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

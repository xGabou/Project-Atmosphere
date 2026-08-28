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
    T122_OFF(2, "t122_off");

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

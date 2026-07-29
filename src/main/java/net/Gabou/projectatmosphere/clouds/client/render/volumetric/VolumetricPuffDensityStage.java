package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import org.jetbrains.annotations.NotNull;

/** Selects a diagnostic cut through the native direct-PUFF density pipeline. */
public enum VolumetricPuffDensityStage {
    FINAL(0, "final"),
    ANALYTIC_ALL(1, "analytic"),
    ANALYTIC_INDEXED(2, "indexed"),
    WEATHER_ENVELOPE(3, "envelope"),
    PRE_EROSION(4, "pre_erosion"),
    MORPHOLOGY_GAP(5, "morphology_gap"),
    FINAL_PUFF_ONLY(6, "final_puff_only"),
    CONTINUOUS_ALL(7, "carrier"),
    CONTINUOUS_MIN(8, "carrier_min"),
    CONTINUOUS_MEDIAN(9, "carrier_median"),
    CONTINUOUS_MAX(10, "carrier_max"),
    CONTINUOUS_ENVELOPE(11, "carrier_envelope"),
    CONTINUOUS_BILLOW(12, "carrier_billow");

    private final int shaderId;
    private final String serializedName;

    VolumetricPuffDensityStage(int shaderId, @NotNull String serializedName) {
        this.shaderId = shaderId;
        this.serializedName = serializedName;
    }

    public int shaderId() {
        return shaderId;
    }

    public @NotNull String serializedName() {
        return serializedName;
    }

    public boolean isDiagnostic() {
        return this != FINAL;
    }
}

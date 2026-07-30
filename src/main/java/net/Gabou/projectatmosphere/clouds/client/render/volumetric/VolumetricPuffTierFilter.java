package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import org.jetbrains.annotations.NotNull;

/** Selects which persisted native-PUFF tier participates in a causal render cut. */
public enum VolumetricPuffTierFilter {
    ALL(-1, "all"),
    BASE(0, "base"),
    MIDDLE(1, "middle"),
    CROWN(2, "crown"),
    UNKNOWN(3, "unknown");

    private final int shaderId;
    private final String serializedName;

    VolumetricPuffTierFilter(int shaderId, @NotNull String serializedName) {
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

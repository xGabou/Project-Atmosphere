package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import org.jetbrains.annotations.NotNull;

/** Selects one global PUFF shape source for controlled native-renderer A/B tests. */
public enum VolumetricPuffShapeMode {
    FALLBACK_ONLY(0, "fallback", false),
    HYBRID(1, "hybrid", true),
    DIRECT_ONLY(2, "direct", true);

    private final int shaderId;
    private final String serializedName;
    private final boolean usesDirectDescriptors;

    VolumetricPuffShapeMode(
            int shaderId,
            @NotNull String serializedName,
            boolean usesDirectDescriptors
    ) {
        this.shaderId = shaderId;
        this.serializedName = serializedName;
        this.usesDirectDescriptors = usesDirectDescriptors;
    }

    public int shaderId() {
        return shaderId;
    }

    public @NotNull String serializedName() {
        return serializedName;
    }

    public boolean usesDirectDescriptors() {
        return usesDirectDescriptors;
    }
}

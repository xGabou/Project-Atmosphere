package net.Gabou.projectatmosphere.clouds.client.render.field;

import org.jetbrains.annotations.NotNull;

/**
 * Diagnostic filter for isolating synced CloudField snapshots while tuning the
 * renderer. Normal gameplay should use ALL.
 */
public enum CloudFieldVolumeRenderFilter {
    ALL("all"),
    MANUAL("manual"),
    WEATHER("weather"),
    NEAREST("nearest"),
    FIRST("first");

    private final String serializedName;

    CloudFieldVolumeRenderFilter(@NotNull String serializedName) {
        this.serializedName = serializedName;
    }

    public @NotNull String serializedName() {
        return serializedName;
    }
}

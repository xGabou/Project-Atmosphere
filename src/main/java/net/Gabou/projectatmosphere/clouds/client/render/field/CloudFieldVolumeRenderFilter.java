package net.Gabou.projectatmosphere.clouds.client.render.field;

import org.jetbrains.annotations.NotNull;

/**
 * Client-side test filter for isolating CloudField snapshot volume rendering.
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

package net.Gabou.projectatmosphere.clouds.client.render.field;

import org.jetbrains.annotations.NotNull;

/**
 * Debug modes understood by the CloudField volume prototype shader.
 */
public enum CloudFieldVolumeRenderMode {
    NORMAL(0, "normal"),
    BOUNDS(1, "bounds"),
    HORIZONTAL(2, "horizontal"),
    HEIGHT(3, "height"),
    VERTICAL(4, "vertical"),
    DENSITY(5, "density"),
    SOURCE(6, "source");

    private final int shaderId;
    private final String serializedName;

    CloudFieldVolumeRenderMode(int shaderId, @NotNull String serializedName) {
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

package net.Gabou.projectatmosphere.clouds.client.render.field;

import org.jetbrains.annotations.NotNull;

/**
 * Controls visualization of the dedicated CloudField upsample/composite pass.
 */
public enum CloudFieldCompositeDebugMode {
    FINAL(0, "final"),
    COLOR(1, "color"),
    DEPTH(2, "depth"),
    ALIGNMENT(3, "alignment"),
    ALPHA(4, "alpha"),
    SPATIAL(5, "spatial"),
    SELECTED_NEIGHBOR(6, "selected_neighbor"),
    SCENE_REJECTION(7, "scene_rejection");

    private final int shaderId;
    private final String serializedName;

    CloudFieldCompositeDebugMode(int shaderId, @NotNull String serializedName) {
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

package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import org.jetbrains.annotations.NotNull;

/** Selects which temporal-raymarch stage is written to the cloud target. */
public enum VolumetricCloudRaymarchDebugView {
    FINAL(0, "final"),
    CURRENT_ONLY(1, "current"),
    HISTORY_ONLY(2, "history"),
    HISTORY_REJECTION(3, "history_rejection"),
    HISTORY_DEPTH_SPACE(4, "history_depth_space");

    private final int shaderId;
    private final String serializedName;

    VolumetricCloudRaymarchDebugView(int shaderId, @NotNull String serializedName) {
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

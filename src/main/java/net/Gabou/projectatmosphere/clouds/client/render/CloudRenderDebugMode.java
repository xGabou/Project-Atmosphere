package net.Gabou.projectatmosphere.clouds.client.render;

import org.jetbrains.annotations.NotNull;

public enum CloudRenderDebugMode {
    OFF(0, "off"),
    BOUNDS(1, "bounds"),
    RAY_ENTRY_EXIT(2, "ray_entry_exit"),
    LOCAL_RGB(3, "local_rgb"),
    VERTICAL_Y01(4, "vertical_y01"),
    PRIMARY_MASS(5, "primary_mass"),
    VERTICAL_ENVELOPE(6, "vertical_envelope"),
    FINAL_DENSITY(7, "final_density"),
    UNLIT_NO_LIGHTING(8, "unlit_no_lighting");

    private static volatile CloudRenderDebugMode current = OFF;

    private final int id;
    private final String serializedName;

    CloudRenderDebugMode(int id, @NotNull String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int id() {
        return id;
    }

    public @NotNull String serializedName() {
        return serializedName;
    }

    public boolean isActive() {
        return this != OFF;
    }

    public static @NotNull CloudRenderDebugMode current() {
        return current;
    }

    public static void setCurrent(@NotNull CloudRenderDebugMode mode) {
        current = mode;
    }
}

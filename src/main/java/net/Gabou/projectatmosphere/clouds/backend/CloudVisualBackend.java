package net.Gabou.projectatmosphere.clouds.backend;

public enum CloudVisualBackend {
    PA_NATIVE,
    SIMPLE_CLOUDS,
    DISABLED;

    public static CloudVisualBackend byName(String name, CloudVisualBackend fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return CloudVisualBackend.valueOf(name.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

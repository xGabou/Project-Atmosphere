package net.Gabou.projectatmosphere.clouds.backend;

public enum CloudMigrationDirection {
    NONE,
    PA_NATIVE_TO_SIMPLE_CLOUDS,
    SIMPLE_CLOUDS_TO_PA_NATIVE;

    public static CloudMigrationDirection byName(String name, CloudMigrationDirection fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return CloudMigrationDirection.valueOf(name.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

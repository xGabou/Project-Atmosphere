package net.Gabou.projectatmosphere.clouds.backend;

public enum CloudMigrationStatus {
    NONE("none"),
    SKIPPED_FRESH_WORLD("skipped, fresh world"),
    SKIPPED_NO_SOURCE_DATA("skipped, no source cloud data"),
    SKIPPED_ALREADY_MIGRATED("skipped, already migrated"),
    COMPLETED_PA_TO_SIMPLE_CLOUDS("completed, PA_NATIVE -> SIMPLE_CLOUDS"),
    COMPLETED_SIMPLE_CLOUDS_TO_PA("completed, SIMPLE_CLOUDS -> PA_NATIVE"),
    FAILED("failed");

    private final String label;

    CloudMigrationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static CloudMigrationStatus byName(String name, CloudMigrationStatus fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return CloudMigrationStatus.valueOf(name.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

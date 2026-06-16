package net.Gabou.projectatmosphere.clouds.backend;

public record CloudBackendStatus(
        CloudVisualBackend currentBackend,
        CloudVisualBackend lastBackend,
        boolean simpleCloudsLoaded,
        int paCloudsStored,
        int paCloudsRendered,
        int bridgeSnapshotsStored,
        CloudMigrationDirection lastMigrationDirection,
        CloudMigrationStatus migrationStatus,
        boolean duplicateVisualCloudRisk
) {
}

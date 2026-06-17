package net.Gabou.projectatmosphere.clouds.backend;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudService;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.clouds.simulation.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CloudBackendMigrationManager {
    private static final int SNAPSHOT_CAPTURE_INTERVAL_TICKS = 200;
    private static final int MAX_BRIDGE_SNAPSHOTS = 160;

    private CloudBackendMigrationManager() {
    }

    public static CloudVisualBackend tick(@NotNull ServerLevel level, @NotNull AtmosphereCloudService service) {
        CloudVisualBackend current = CloudBackendResolver.resolve(level);
        CloudBackendMigrationSavedData data = CloudBackendMigrationSavedData.get(level);
        CloudBackendMigrationState state = data.state();
        CloudVisualBackend previous = state.getCurrentCloudBackend();
        boolean firstObservation = state.getLastMigrationGameTime() < 0L
                && state.getLastMigrationStatus() == CloudMigrationStatus.NONE
                && previous == CloudVisualBackend.DISABLED;

        if (current != previous) {
            handleBackendChange(level, service, state, previous, current, firstObservation);
            state.setLastCloudBackend(inferReportedPreviousBackend(level, state, previous, current, firstObservation));
            state.setCurrentCloudBackend(current);
            data.markChanged();
        } else if (state.getCurrentCloudBackend() != current) {
            state.setCurrentCloudBackend(current);
            data.markChanged();
        }

        if (current == CloudVisualBackend.PA_NATIVE && level.getGameTime() % SNAPSHOT_CAPTURE_INTERVAL_TICKS == 0L) {
            capturePaNativeSnapshots(level);
        }
        return current;
    }

    public static void captureSimpleCloudSnapshots(@NotNull ServerLevel level, @NotNull List<CloudBackendBridgeSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        CloudBackendMigrationSavedData data = CloudBackendMigrationSavedData.get(level);
        data.state().replaceBridgeSnapshots(CloudVisualBackend.SIMPLE_CLOUDS.name(), snapshots, MAX_BRIDGE_SNAPSHOTS);
        data.markChanged();
    }

    public static @NotNull CloudBackendStatus status(@NotNull ServerLevel level) {
        CloudBackendMigrationState state = CloudBackendMigrationSavedData.get(level).state();
        CloudVisualBackend current = CloudBackendResolver.resolve(level);
        int stored = CloudRegionStateStore.size(level);
        int rendered = current == CloudVisualBackend.PA_NATIVE
                ? CloudRegionStateStore.getActiveRegions(level).size()
                : 0;
        return new CloudBackendStatus(
                current,
                state.getLastCloudBackend(),
                AtmosphereCloudServices.isSimpleCloudsLoaded(),
                stored,
                rendered,
                state.bridgeSnapshotCount(),
                state.getLastMigrationDirection(),
                state.getLastMigrationStatus(),
                current == CloudVisualBackend.SIMPLE_CLOUDS && rendered > 0
        );
    }

    private static CloudVisualBackend inferReportedPreviousBackend(
            ServerLevel level,
            CloudBackendMigrationState state,
            CloudVisualBackend previous,
            CloudVisualBackend current,
            boolean firstObservation
    ) {
        if (!firstObservation) {
            return previous;
        }
        if (current == CloudVisualBackend.SIMPLE_CLOUDS && CloudRegionStateStore.size(level) > 0) {
            return CloudVisualBackend.PA_NATIVE;
        }
        if (current == CloudVisualBackend.PA_NATIVE && state.hasBridgeSnapshots(CloudVisualBackend.SIMPLE_CLOUDS.name())) {
            return CloudVisualBackend.SIMPLE_CLOUDS;
        }
        return previous;
    }

    private static void handleBackendChange(
            ServerLevel level,
            AtmosphereCloudService service,
            CloudBackendMigrationState state,
            CloudVisualBackend previous,
            CloudVisualBackend current,
            boolean firstObservation
    ) {
        if (firstObservation) {
            boolean paCloudsExist = CloudRegionStateStore.size(level) > 0;
            if (current == CloudVisualBackend.SIMPLE_CLOUDS && paCloudsExist) {
                migratePaNativeToSimpleClouds(level, service, state);
                return;
            }
            if (current == CloudVisualBackend.PA_NATIVE && state.hasBridgeSnapshots(CloudVisualBackend.SIMPLE_CLOUDS.name())) {
                migrateSimpleCloudsToPaNative(level, state);
                return;
            }
            state.setLastMigrationStatus(!paCloudsExist && state.bridgeSnapshotCount() == 0
                    ? CloudMigrationStatus.SKIPPED_FRESH_WORLD
                    : CloudMigrationStatus.SKIPPED_NO_SOURCE_DATA);
            return;
        }

        if (previous == CloudVisualBackend.PA_NATIVE && current == CloudVisualBackend.SIMPLE_CLOUDS) {
            migratePaNativeToSimpleClouds(level, service, state);
            return;
        }
        if (previous == CloudVisualBackend.SIMPLE_CLOUDS && current == CloudVisualBackend.PA_NATIVE) {
            migrateSimpleCloudsToPaNative(level, state);
            return;
        }

        state.setLastMigrationStatus(CloudMigrationStatus.SKIPPED_NO_SOURCE_DATA);
    }

    private static void migratePaNativeToSimpleClouds(ServerLevel level, AtmosphereCloudService service, CloudBackendMigrationState state) {
        if (state.isPaCloudsMirroredToSimpleClouds()) {
            state.setLastMigrationStatus(CloudMigrationStatus.SKIPPED_ALREADY_MIGRATED);
            return;
        }

        List<CloudBackendBridgeSnapshot> snapshots = createPaNativeSnapshots(level);
        if (snapshots.isEmpty()) {
            state.setLastMigrationStatus(CloudMigrationStatus.SKIPPED_NO_SOURCE_DATA);
            return;
        }

        int mirrored = service.mirrorPaNativeClouds(level, snapshots);
        if (mirrored > 0) {
            state.replaceBridgeSnapshots(CloudVisualBackend.PA_NATIVE.name(), snapshots, MAX_BRIDGE_SNAPSHOTS);
            state.setPaCloudsMirroredToSimpleClouds(true);
            state.setLastMigrationDirection(CloudMigrationDirection.PA_NATIVE_TO_SIMPLE_CLOUDS);
            state.setLastMigrationGameTime(level.getGameTime());
            state.setLastMigrationStatus(CloudMigrationStatus.COMPLETED_PA_TO_SIMPLE_CLOUDS);
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Mirrored {} PA native clouds to Simple Clouds.", mirrored);
            return;
        }

        state.setLastMigrationStatus(CloudMigrationStatus.FAILED);
        ProjectAtmosphere.LOGGER.warn("[Atmosphere] PA -> Simple Clouds migration found source data but did not create Simple Clouds regions.");
    }

    private static void migrateSimpleCloudsToPaNative(ServerLevel level, CloudBackendMigrationState state) {
        if (state.isSimpleCloudsMirroredToPa()) {
            state.setLastMigrationStatus(CloudMigrationStatus.SKIPPED_ALREADY_MIGRATED);
            return;
        }

        List<CloudBackendBridgeSnapshot> snapshots = state.getBridgeSnapshots(CloudVisualBackend.SIMPLE_CLOUDS.name());
        if (snapshots.isEmpty()) {
            state.setLastMigrationStatus(CloudMigrationStatus.SKIPPED_NO_SOURCE_DATA);
            return;
        }

        int created = 0;
        for (CloudBackendBridgeSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            CloudRegionState stateRegion = createPaNativeRegion(level, snapshot);
            if (stateRegion != null) {
                created++;
            }
        }

        if (created > 0) {
            state.setSimpleCloudsMirroredToPa(true);
            state.setLastMigrationDirection(CloudMigrationDirection.SIMPLE_CLOUDS_TO_PA_NATIVE);
            state.setLastMigrationGameTime(level.getGameTime());
            state.setLastMigrationStatus(CloudMigrationStatus.COMPLETED_SIMPLE_CLOUDS_TO_PA);
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Mirrored {} Simple Clouds snapshots to PA native clouds.", created);
            return;
        }

        state.setLastMigrationStatus(CloudMigrationStatus.FAILED);
        ProjectAtmosphere.LOGGER.warn("[Atmosphere] Simple Clouds -> PA migration found snapshots but did not create PA regions.");
    }

    private static CloudRegionState createPaNativeRegion(ServerLevel level, CloudBackendBridgeSnapshot snapshot) {
        Vec3 center = new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
        float radius = clamp((float) snapshot.radius(), 48.0F, 1400.0F);
        float height = clamp((float) snapshot.height(), 16.0F, 420.0F);
        float baseY = clamp((float) snapshot.y() - height * 0.35F, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1.0F);
        float topY = clamp(baseY + height, baseY + 1.0F, level.getMaxBuildHeight());
        float density = clamp01((float) snapshot.density());
        float coverage = clamp01((float) snapshot.coverage());
        RegionInstanceKey sourceRegion = RegionInstanceKey.from(BlockPos.containing(center));
        String paType = mapSimpleCloudsTypeToPa(snapshot.sourceTypeId());

        return CloudRegionManager.getInstance().createCloudRegion(
                level,
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                0.35F,
                sourceRegion,
                paType
        );
    }

    private static void capturePaNativeSnapshots(ServerLevel level) {
        List<CloudBackendBridgeSnapshot> snapshots = createPaNativeSnapshots(level);
        if (snapshots.isEmpty()) {
            return;
        }
        CloudBackendMigrationSavedData data = CloudBackendMigrationSavedData.get(level);
        data.state().replaceBridgeSnapshots(CloudVisualBackend.PA_NATIVE.name(), snapshots, MAX_BRIDGE_SNAPSHOTS);
        data.markChanged();
    }

    private static List<CloudBackendBridgeSnapshot> createPaNativeSnapshots(ServerLevel level) {
        List<CloudBackendBridgeSnapshot> snapshots = new ArrayList<>();
        for (CloudRegionState region : CloudRegionStateStore.getActiveRegions(level)) {
            if (region != null && region.isActive()) {
                snapshots.add(CloudBackendBridgeSnapshot.fromPaRegion(region, level.getGameTime()));
            }
        }
        return snapshots;
    }

    private static String mapSimpleCloudsTypeToPa(String sourceTypeId) {
        String id = sourceTypeId == null ? "" : sourceTypeId.toLowerCase(Locale.ROOT);
        if (id.contains("severe") || id.contains("cumulonimbus") || id.contains("thunder") || id.contains("tsegrus")) {
            return "cumulonimbus_capillatus";
        }
        if (id.contains("congestus") || id.contains("tower") || id.contains("tall")) {
            return "cumulus_congestus";
        }
        if (id.contains("stratocumulus")) {
            return "stratocumulus";
        }
        if (id.contains("nimbostratus") || id.contains("overcast") || id.contains("heavy_stratus")) {
            return "nimbostratus";
        }
        if (id.contains("stratus")) {
            return "stratus_nebulosus";
        }
        if (id.contains("cirrus") || id.contains("wispy") || id.contains("filament")) {
            return "cirrus";
        }
        if (id.contains("mediocris") || id.contains("cumulus")) {
            return "cumulus_mediocris";
        }
        if (!CloudTypeRegistry.get("cumulus_humilis").isPresent()) {
            return CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        }
        return "cumulus_humilis";
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}

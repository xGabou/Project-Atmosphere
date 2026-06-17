package net.Gabou.projectatmosphere.clouds.backend;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CloudBackendMigrationState {
    public static final int CURRENT_VERSION = 1;

    private static final String LAST_BACKEND = "LastCloudBackend";
    private static final String CURRENT_BACKEND = "CurrentCloudBackend";
    private static final String LAST_MIGRATION_DIRECTION = "LastMigrationDirection";
    private static final String LAST_MIGRATION_GAME_TIME = "LastMigrationGameTime";
    private static final String PA_MIRRORED_TO_SIMPLE_CLOUDS = "PaCloudsMirroredToSimpleClouds";
    private static final String SIMPLE_CLOUDS_MIRRORED_TO_PA = "SimpleCloudsMirroredToPa";
    private static final String MIGRATION_VERSION = "MigrationVersion";
    private static final String LAST_MIGRATION_STATUS = "LastMigrationStatus";
    private static final String BRIDGE_SNAPSHOTS = "BridgeSnapshots";

    private CloudVisualBackend lastCloudBackend = CloudVisualBackend.DISABLED;
    private CloudVisualBackend currentCloudBackend = CloudVisualBackend.DISABLED;
    private CloudMigrationDirection lastMigrationDirection = CloudMigrationDirection.NONE;
    private long lastMigrationGameTime = -1L;
    private boolean paCloudsMirroredToSimpleClouds;
    private boolean simpleCloudsMirroredToPa;
    private int migrationVersion = CURRENT_VERSION;
    private CloudMigrationStatus lastMigrationStatus = CloudMigrationStatus.NONE;
    private final List<CloudBackendBridgeSnapshot> bridgeSnapshots = new ArrayList<>();

    public CloudVisualBackend getLastCloudBackend() {
        return lastCloudBackend;
    }

    public void setLastCloudBackend(CloudVisualBackend lastCloudBackend) {
        this.lastCloudBackend = lastCloudBackend == null ? CloudVisualBackend.DISABLED : lastCloudBackend;
    }

    public CloudVisualBackend getCurrentCloudBackend() {
        return currentCloudBackend;
    }

    public void setCurrentCloudBackend(CloudVisualBackend currentCloudBackend) {
        this.currentCloudBackend = currentCloudBackend == null ? CloudVisualBackend.DISABLED : currentCloudBackend;
    }

    public CloudMigrationDirection getLastMigrationDirection() {
        return lastMigrationDirection;
    }

    public void setLastMigrationDirection(CloudMigrationDirection lastMigrationDirection) {
        this.lastMigrationDirection = lastMigrationDirection == null ? CloudMigrationDirection.NONE : lastMigrationDirection;
    }

    public long getLastMigrationGameTime() {
        return lastMigrationGameTime;
    }

    public void setLastMigrationGameTime(long lastMigrationGameTime) {
        this.lastMigrationGameTime = lastMigrationGameTime;
    }

    public boolean isPaCloudsMirroredToSimpleClouds() {
        return paCloudsMirroredToSimpleClouds;
    }

    public void setPaCloudsMirroredToSimpleClouds(boolean paCloudsMirroredToSimpleClouds) {
        this.paCloudsMirroredToSimpleClouds = paCloudsMirroredToSimpleClouds;
    }

    public boolean isSimpleCloudsMirroredToPa() {
        return simpleCloudsMirroredToPa;
    }

    public void setSimpleCloudsMirroredToPa(boolean simpleCloudsMirroredToPa) {
        this.simpleCloudsMirroredToPa = simpleCloudsMirroredToPa;
    }

    public int getMigrationVersion() {
        return migrationVersion;
    }

    public CloudMigrationStatus getLastMigrationStatus() {
        return lastMigrationStatus;
    }

    public void setLastMigrationStatus(CloudMigrationStatus lastMigrationStatus) {
        this.lastMigrationStatus = lastMigrationStatus == null ? CloudMigrationStatus.NONE : lastMigrationStatus;
    }

    public @NotNull List<CloudBackendBridgeSnapshot> getBridgeSnapshots() {
        return List.copyOf(bridgeSnapshots);
    }

    public @NotNull List<CloudBackendBridgeSnapshot> getBridgeSnapshots(String sourceBackend) {
        List<CloudBackendBridgeSnapshot> result = new ArrayList<>();
        for (CloudBackendBridgeSnapshot snapshot : bridgeSnapshots) {
            if (snapshot != null && sourceBackend.equals(snapshot.sourceBackend())) {
                result.add(snapshot);
            }
        }
        return List.copyOf(result);
    }

    public void replaceBridgeSnapshots(String sourceBackend, List<CloudBackendBridgeSnapshot> snapshots, int maxSnapshots) {
        bridgeSnapshots.removeIf(snapshot -> snapshot == null || sourceBackend.equals(snapshot.sourceBackend()));
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        int limit = Math.max(1, maxSnapshots);
        for (CloudBackendBridgeSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            bridgeSnapshots.add(snapshot);
            if (bridgeSnapshots.size() >= limit) {
                break;
            }
        }
    }

    public int bridgeSnapshotCount() {
        return bridgeSnapshots.size();
    }

    public boolean hasBridgeSnapshots(String sourceBackend) {
        for (CloudBackendBridgeSnapshot snapshot : bridgeSnapshots) {
            if (snapshot != null && sourceBackend.equals(snapshot.sourceBackend())) {
                return true;
            }
        }
        return false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(LAST_BACKEND, lastCloudBackend.name());
        tag.putString(CURRENT_BACKEND, currentCloudBackend.name());
        tag.putString(LAST_MIGRATION_DIRECTION, lastMigrationDirection.name());
        tag.putLong(LAST_MIGRATION_GAME_TIME, lastMigrationGameTime);
        tag.putBoolean(PA_MIRRORED_TO_SIMPLE_CLOUDS, paCloudsMirroredToSimpleClouds);
        tag.putBoolean(SIMPLE_CLOUDS_MIRRORED_TO_PA, simpleCloudsMirroredToPa);
        tag.putInt(MIGRATION_VERSION, migrationVersion);
        tag.putString(LAST_MIGRATION_STATUS, lastMigrationStatus.name());

        ListTag snapshots = new ListTag();
        for (CloudBackendBridgeSnapshot snapshot : bridgeSnapshots) {
            if (snapshot != null) {
                snapshots.add(snapshot.save());
            }
        }
        tag.put(BRIDGE_SNAPSHOTS, snapshots);
        return tag;
    }

    public static CloudBackendMigrationState load(CompoundTag tag) {
        CloudBackendMigrationState state = new CloudBackendMigrationState();
        if (tag == null) {
            return state;
        }
        state.lastCloudBackend = CloudVisualBackend.byName(tag.getString(LAST_BACKEND), CloudVisualBackend.DISABLED);
        state.currentCloudBackend = CloudVisualBackend.byName(tag.getString(CURRENT_BACKEND), CloudVisualBackend.DISABLED);
        state.lastMigrationDirection = CloudMigrationDirection.byName(tag.getString(LAST_MIGRATION_DIRECTION), CloudMigrationDirection.NONE);
        state.lastMigrationGameTime = tag.contains(LAST_MIGRATION_GAME_TIME) ? tag.getLong(LAST_MIGRATION_GAME_TIME) : -1L;
        state.paCloudsMirroredToSimpleClouds = tag.getBoolean(PA_MIRRORED_TO_SIMPLE_CLOUDS);
        state.simpleCloudsMirroredToPa = tag.getBoolean(SIMPLE_CLOUDS_MIRRORED_TO_PA);
        state.migrationVersion = tag.contains(MIGRATION_VERSION) ? tag.getInt(MIGRATION_VERSION) : CURRENT_VERSION;
        state.lastMigrationStatus = CloudMigrationStatus.byName(tag.getString(LAST_MIGRATION_STATUS), CloudMigrationStatus.NONE);
        if (tag.contains(BRIDGE_SNAPSHOTS, Tag.TAG_LIST)) {
            ListTag snapshots = tag.getList(BRIDGE_SNAPSHOTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < snapshots.size(); i++) {
                state.bridgeSnapshots.add(CloudBackendBridgeSnapshot.load(snapshots.getCompound(i)));
            }
        }
        return state;
    }
}

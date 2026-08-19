package net.Gabou.projectatmosphere.clouds.field.network;

import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.runtime.CloudFieldRuntimeManager;
import net.Gabou.projectatmosphere.platform.config.AtmosphereConfig;
import net.Gabou.projectatmosphere.platform.network.AtmosphereNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Syncs server CloudField snapshots to clients. It does not replace the legacy
 * region packet yet.
 */
public final class CloudFieldSyncManager {
    private static final long SYNC_INTERVAL_TICKS = 20L;
    private static final long FULL_RESYNC_INTERVAL_TICKS = 600L;
    private static final double INTEREST_MARGIN = 512.0D;
    private static final double MIN_INTEREST_RADIUS = 1024.0D;
    private static final double PREFETCH_TICKS = 200.0D;

    private static final Map<UUID, PlayerSyncState> PLAYER_STATES = new ConcurrentHashMap<>();
    private static volatile long lastSyncTick;
    private static volatile int lastSyncedCount;

    private CloudFieldSyncManager() {
    }

    public static void syncPlayers(ServerLevel level) {
        if (level == null || level.getGameTime() % SYNC_INTERVAL_TICKS != 0L) {
            return;
        }

        Collection<CloudFieldSnapshot> snapshots =
                CloudFieldRuntimeManager.getInstance().ensureCurrent(level).fields();
        int sent = 0;
        for (ServerPlayer player : level.players()) {
            List<CloudFieldSnapshot> interested = interestedSnapshots(player, snapshots);
            PlayerSyncState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerSyncState());
            if (!state.initialized
                    || level.getGameTime() - state.lastFullSyncTick >= FULL_RESYNC_INTERVAL_TICKS) {
                sendFull(player, interested);
                state.replace(interested, level.getGameTime());
                sent += interested.size();
                continue;
            }

            Map<UUID, Long> nextFingerprints = fingerprints(interested);
            List<CloudFieldSnapshot> updated = new ArrayList<>();
            for (CloudFieldSnapshot snapshot : interested) {
                Long previous = state.fingerprints.get(snapshot.fieldId());
                long next = nextFingerprints.get(snapshot.fieldId());
                if (previous == null || previous.longValue() != next) {
                    updated.add(snapshot);
                }
            }
            Set<UUID> removed = new HashSet<>(state.fingerprints.keySet());
            removed.removeAll(nextFingerprints.keySet());
            if (!updated.isEmpty() || !removed.isEmpty()) {
                AtmosphereNetwork.sendToPlayer(player, new CloudFieldDeltaPacket(updated, removed));
                sent += updated.size();
            }
            state.fingerprints = Map.copyOf(nextFingerprints);
        }
        recordSync(level.getGameTime(), sent);
    }

    public static void syncPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Collection<CloudFieldSnapshot> snapshots =
                CloudFieldRuntimeManager.getInstance().ensureCurrent(level).fields();
        List<CloudFieldSnapshot> interested = interestedSnapshots(player, snapshots);
        recordSync(level.getGameTime(), interested.size());
        sendFull(player, interested);
        PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerSyncState())
                .replace(interested, level.getGameTime());
    }

    public static void forgetPlayer(UUID playerId) {
        if (playerId != null) {
            PLAYER_STATES.remove(playerId);
        }
    }

    public static long getLastSyncTick() {
        return lastSyncTick;
    }

    public static int getLastSyncedCount() {
        return lastSyncedCount;
    }

    private static void recordSync(long gameTime, int count) {
        lastSyncTick = Math.max(0L, gameTime);
        lastSyncedCount = Math.max(0, count);
    }

    private static void sendFull(ServerPlayer player, Collection<CloudFieldSnapshot> snapshots) {
        if (player == null) {
            return;
        }
        AtmosphereNetwork.sendToPlayer(
                player,
                new SyncCloudFieldsPacket(snapshots == null ? List.of() : snapshots));
    }

    private static List<CloudFieldSnapshot> interestedSnapshots(
            ServerPlayer player,
            Collection<CloudFieldSnapshot> snapshots
    ) {
        if (player == null || snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        double configuredDistance = AtmosphereConfig.clouds().cloudRenderDistance();
        double baseInterest = Math.max(MIN_INTEREST_RADIUS, configuredDistance) + INTEREST_MARGIN;
        List<CloudFieldSnapshot> interested = new ArrayList<>();
        for (CloudFieldSnapshot snapshot : snapshots) {
            if (snapshot == null
                    || !player.level().dimension().location().toString().equals(snapshot.dimensionId())) {
                continue;
            }
            double radius = baseInterest + snapshot.radius();
            double radiusSqr = radius * radius;
            double dx = snapshot.center().x() - player.getX();
            double dz = snapshot.center().z() - player.getZ();
            double futureX = snapshot.center().x() + snapshot.windVector().x() * PREFETCH_TICKS;
            double futureZ = snapshot.center().z() + snapshot.windVector().z() * PREFETCH_TICKS;
            double futureDx = futureX - player.getX();
            double futureDz = futureZ - player.getZ();
            if (dx * dx + dz * dz <= radiusSqr
                    || futureDx * futureDx + futureDz * futureDz <= radiusSqr) {
                interested.add(snapshot);
            }
        }
        interested.sort((left, right) -> left.fieldId().compareTo(right.fieldId()));
        return List.copyOf(interested);
    }

    private static Map<UUID, Long> fingerprints(Collection<CloudFieldSnapshot> snapshots) {
        Map<UUID, Long> fingerprints = new LinkedHashMap<>();
        for (CloudFieldSnapshot snapshot : snapshots) {
            fingerprints.put(snapshot.fieldId(), fingerprint(snapshot));
        }
        return fingerprints;
    }

    private static long fingerprint(CloudFieldSnapshot snapshot) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, quantize(snapshot.center().x(), 4.0D));
        hash = mix(hash, quantize(snapshot.center().y(), 4.0D));
        hash = mix(hash, quantize(snapshot.center().z(), 4.0D));
        hash = mix(hash, quantize(snapshot.radius(), 8.0D));
        hash = mix(hash, quantize(snapshot.baseY(), 8.0D));
        hash = mix(hash, quantize(snapshot.topY(), 8.0D));
        hash = mix(hash, quantize(snapshot.density(), 1024.0D));
        hash = mix(hash, quantize(snapshot.coverage(), 1024.0D));
        hash = mix(hash, quantize(snapshot.growth(), 1024.0D));
        hash = mix(hash, quantize(snapshot.decay(), 1024.0D));
        hash = mix(hash, quantize(snapshot.humidityInfluence(), 1024.0D));
        hash = mix(hash, quantize(snapshot.windVector().x(), 4096.0D));
        hash = mix(hash, quantize(snapshot.windVector().z(), 4096.0D));
        hash = mix(hash, quantize(snapshot.verticalDevelopment(), 1024.0D));
        hash = mix(hash, quantize(snapshot.stormPotential(), 1024.0D));
        hash = mix(hash, snapshot.cloudTypeId().hashCode());
        hash = mix(hash, snapshot.morphologyFamily().ordinal());
        hash = mix(hash, snapshot.morphologyMembership().groupId().getMostSignificantBits());
        hash = mix(hash, snapshot.morphologyMembership().groupId().getLeastSignificantBits());
        hash = mix(hash, snapshot.morphologyMembership().memberIndex());
        hash = mix(hash, snapshot.morphologyMembership().memberCount());
        hash = mix(hash, snapshot.morphologyMembership().layoutVersion());
        hash = mix(hash, snapshot.morphologyMembership().memberTier().ordinal());
        hash = mix(hash, quantize(snapshot.anvilStrength(), 1024.0D));
        hash = mix(hash, quantize(snapshot.precipitationIntensity(), 1024.0D));
        hash = mix(hash, snapshot.targetCloudletCount());
        return hash;
    }

    private static long quantize(double value, double scale) {
        return Double.isFinite(value) ? Math.round(value * scale) : 0L;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }

    private static final class PlayerSyncState {
        private Map<UUID, Long> fingerprints = Map.of();
        private long lastFullSyncTick = Long.MIN_VALUE;
        private boolean initialized;

        private void replace(Collection<CloudFieldSnapshot> snapshots, long gameTime) {
            fingerprints = Map.copyOf(CloudFieldSyncManager.fingerprints(snapshots));
            lastFullSyncTick = gameTime;
            initialized = true;
        }
    }
}

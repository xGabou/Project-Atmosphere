package net.Gabou.projectatmosphere.clouds.field.network;

import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.runtime.CloudFieldRuntimeManager;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;
import java.util.List;

/**
 * Syncs server CloudField snapshots to clients. It does not replace the legacy
 * region packet yet.
 */
public final class CloudFieldSyncManager {
    private static volatile long lastSyncTick;
    private static volatile int lastSyncedCount;

    private CloudFieldSyncManager() {
    }

    public static void syncPlayers(ServerLevel level) {
        if (level == null || level.getGameTime() % 20L != 0L) {
            return;
        }

        Collection<CloudFieldSnapshot> snapshots =
                CloudFieldRuntimeManager.getInstance().ensureCurrent(level).fields();
        recordSync(level.getGameTime(), snapshots.size());

        for (ServerPlayer player : level.players()) {
            send(player, snapshots);
        }
    }

    public static void syncPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Collection<CloudFieldSnapshot> snapshots =
                CloudFieldRuntimeManager.getInstance().ensureCurrent(level).fields();
        recordSync(level.getGameTime(), snapshots.size());
        send(player, snapshots);
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

    private static void send(ServerPlayer player, Collection<CloudFieldSnapshot> snapshots) {
        if (player == null) {
            return;
        }
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncCloudFieldsPacket(snapshots == null ? List.of() : snapshots)
        );
    }
}

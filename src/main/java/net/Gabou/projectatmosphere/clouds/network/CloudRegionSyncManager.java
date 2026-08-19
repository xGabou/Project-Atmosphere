package net.Gabou.projectatmosphere.clouds.network;

import net.Gabou.projectatmosphere.clouds.simulation.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.platform.network.AtmosphereNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Synchronise les régions de nuage backend vers les clients.
 * Cette classe envoie seulement des CloudRegionRenderData.
 */
public final class CloudRegionSyncManager {
    private static volatile long lastSyncTick;
    private static volatile int lastSyncedCount;

    private CloudRegionSyncManager() {

    }

    /**
     * Synchronise les régions de nuage actives avec tous les joueurs du niveau.
     *
     * @param level niveau serveur
     */
    public static void syncPlayers(ServerLevel level) {
        if (level == null || AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return;
        }
        if (level.getGameTime() % 20L != 0L) {
            return;
        }

        Collection<CloudRegionRenderData> renderData =
                CloudRegionManager.getInstance().getActiveRenderData(level);
        recordSync(level.getGameTime(), renderData.size());

        for (ServerPlayer player : level.players()) {
            send(player, renderData);
        }
    }

    /**
     * Synchronise les régions de nuage actives avec un joueur.
     *
     * @param player joueur cible
     */
    public static void syncPlayer(ServerPlayer player) {
        if (player == null || AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return;
        }


        ServerLevel level = player.serverLevel();

        Collection<CloudRegionRenderData> renderData =
                CloudRegionManager.getInstance().getActiveRenderData(level);
        recordSync(level.getGameTime(), renderData.size());

        send(player, renderData);
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

    private static void send(ServerPlayer player, Collection<CloudRegionRenderData> renderData) {
        if (player == null) {
            return;
        }
        AtmosphereNetwork.sendToPlayer(player, new SyncCloudRegionsPacket(renderData));
    }
}

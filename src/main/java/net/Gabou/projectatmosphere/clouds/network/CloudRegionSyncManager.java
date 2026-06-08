package net.Gabou.projectatmosphere.clouds.network;

import net.Gabou.projectatmosphere.clouds.simulation.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;

/**
 * Synchronise les régions de nuage backend vers les clients.
 * Cette classe envoie seulement des CloudRegionRenderData.
 */
public final class CloudRegionSyncManager {

    private CloudRegionSyncManager() {

    }

    /**
     * Synchronise les régions de nuage actives avec tous les joueurs du niveau.
     *
     * @param level niveau serveur
     */
    public static void syncPlayers(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (level.getGameTime() % 20L != 0L) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            syncPlayer(player);
        }
    }

    /**
     * Synchronise les régions de nuage actives avec un joueur.
     *
     * @param player joueur cible
     */
    public static void syncPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }


        ServerLevel level = player.serverLevel();

        Collection<CloudRegionRenderData> renderData =
                CloudRegionManager.getInstance().getActiveRenderData(level);

        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncCloudRegionsPacket(renderData)
        );
    }
}

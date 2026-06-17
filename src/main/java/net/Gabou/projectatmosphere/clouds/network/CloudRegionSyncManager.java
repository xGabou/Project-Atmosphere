package net.Gabou.projectatmosphere.clouds.network;

import net.Gabou.projectatmosphere.clouds.simulation.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;

/**
 * Synchronise les rÃ©gions de nuage backend vers les clients.
 * Cette classe envoie seulement des CloudRegionRenderData.
 */
public final class CloudRegionSyncManager {

    private CloudRegionSyncManager() {

    }

    /**
     * Synchronise les rÃ©gions de nuage actives avec tous les joueurs du niveau.
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

        for (ServerPlayer player : level.players()) {
            syncPlayer(player);
        }
    }

    /**
     * Synchronise les rÃ©gions de nuage actives avec un joueur.
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

        PacketDistributor.sendToPlayer(player, new SyncCloudRegionsPacket(renderData));
    }
}

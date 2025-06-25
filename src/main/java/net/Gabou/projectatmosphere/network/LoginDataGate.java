package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.registry.ModNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

public class LoginDataGate {

    public static void sendBiomeSyncPacketIfReady(MinecraftServer server, ServerPlayer player) {
        AtmosphereManager.getPlayerReadyFuture(player).thenRun(() -> {
            // Envoie sur le thread principal (important)
            server.execute(() -> {
                if (player.connection != null && player.connection.connection != null) {
                    ModNetworking.CHANNEL.sendTo(
                            new SyncBiomeDataLoginPacket("Préchargement du biome"),
                            player.connection.connection,
                            NetworkDirection.LOGIN_TO_CLIENT
                    );
                }
            });
        });
    }
}

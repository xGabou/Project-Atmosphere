package net.Gabou.projectatmosphere.auth;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.network.AuthChallengePacket;
import net.Gabou.projectatmosphere.network.AuthChallengeReplyPacket;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;

public final class ServerAuth {
    private ServerAuth() {
    }

    public static boolean onLogin(ServerPlayer player) {
        if (player == null) {
            return true;
        }

        PendingAuthManager.clear(player);

        if (!FMLEnvironment.production) {
            ProjectAtmosphere.LOGGER.info(
                    "Skipping launcher/auth checks for {} because the game is running in a development environment.",
                    player.getGameProfile().getName()
            );
            return true;
        }

        if (player.getUUID() != null
                && player.getUUID().version() == 3
                && AtmoCommonConfig.AUTH_STRICT_OFFLINE_UUID_REJECT.get()) {
            ProjectAtmosphere.LOGGER.warn(
                    "Rejected {} because strict auth mode disallows offline UUID v3 identities.",
                    player.getName().getString()
            );
            player.connection.disconnect(Component.literal("Authentication rejected."));
            return false;
        }

        PendingAuthManager.begin(player);
        return true;
    }

    public static void sendChallenge(ServerPlayer player) {
        if (player == null) {
            return;
        }

        PendingAuthManager.PendingAuth pending = PendingAuthManager.get(player.getUUID());
        if (pending == null) {
            return;
        }

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AuthChallengePacket(pending.nonce()));
    }

    public static boolean enforceLocalLauncherDetection(ServerPlayer player) {
        if (player == null) {
            return true;
        }

        String launcherReason = ClientLauncherGuards.getDetectedReason();
        if (launcherReason == null || launcherReason.isBlank() || !(player.level() instanceof ServerLevel serverLevel)) {
            return true;
        }

        TLauncherDetectedHandler.handle(serverLevel, player, launcherReason);
        onLogout(player);
        return false;
    }

    public static void onTick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long timeoutMs = PendingAuthManager.getTimeoutMs();
        for (Map.Entry<UUID, PendingAuthManager.PendingAuth> entry : PendingAuthManager.snapshot().entrySet()) {
            PendingAuthManager.PendingAuth pending = entry.getValue();
            if (pending == null || now - pending.issuedAtMs() < timeoutMs) {
                continue;
            }

            UUID uuid = entry.getKey();
            PendingAuthManager.clear(uuid);

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }

            ProjectAtmosphere.LOGGER.warn("Auth challenge timed out for {}", player.getName().getString());
            if (AtmoCommonConfig.AUTH_KICK_ON_FAILURE.get()) {
                player.connection.disconnect(Component.literal("Authentication timed out."));
            }
        }
    }

    public static void onLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }

        PendingAuthManager.clear(player);
    }

    public static void handleChallengeReply(ServerPlayer player, AuthChallengeReplyPacket packet) {
        if (player == null || packet == null) {
            return;
        }

        String launcherReason = packet.launcherReason();
        if (launcherReason != null && !launcherReason.isBlank() && player.level() instanceof ServerLevel serverLevel) {
            TLauncherDetectedHandler.handle(serverLevel, player, launcherReason);
            PendingAuthManager.clear(player);
            return;
        }

        PendingAuthManager.PendingAuth pending = PendingAuthManager.get(player.getUUID());
        if (pending == null) {
            markInvalid(player, "unexpected auth reply");
            return;
        }
        if (pending.nonce() != packet.nonce()) {
            markInvalid(player, "nonce mismatch");
            return;
        }
        if (!SharedSecret.verifyResponse(player.getUUID(), packet.nonce(), packet.response())) {
            markInvalid(player, "invalid auth response");
            return;
        }

        PendingAuthManager.clear(player);
        ProjectAtmosphere.LOGGER.info("Auth challenge completed for {}", player.getName().getString());
    }

    private static void markInvalid(ServerPlayer player, String reason) {
        PendingAuthManager.clear(player);
        ProjectAtmosphere.LOGGER.warn(
                "Marked {} as failed auth because verification failed: {}",
                player.getName().getString(),
                reason
        );
        if (AtmoCommonConfig.AUTH_KICK_ON_FAILURE.get()) {
            player.connection.disconnect(Component.literal("Authentication failed."));
        }
    }
}

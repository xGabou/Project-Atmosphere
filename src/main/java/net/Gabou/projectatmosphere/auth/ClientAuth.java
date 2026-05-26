package net.Gabou.projectatmosphere.auth;

import net.Gabou.projectatmosphere.network.AuthChallengePacket;
import net.Gabou.projectatmosphere.network.AuthChallengeReplyPacket;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.minecraft.client.Minecraft;

public final class ClientAuth {
    private ClientAuth() {
    }

    public static void handleChallenge(AuthChallengePacket packet) {
        if (packet == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        NetworkHandler.CHANNEL.sendToServer(new AuthChallengeReplyPacket(
                packet.nonce(),
                SharedSecret.computeResponse(minecraft.player.getUUID(), packet.nonce()),
                ClientLauncherGuards.getDetectedReason()
        ));
    }
}

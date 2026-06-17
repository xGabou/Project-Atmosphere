package net.Gabou.projectatmosphere.auth;

import net.Gabou.projectatmosphere.network.AuthChallengePacket;
import net.Gabou.projectatmosphere.network.AuthChallengeReplyPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

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

        PacketDistributor.sendToServer(new AuthChallengeReplyPacket(
                packet.nonce(),
                SharedSecret.computeResponse(minecraft.player.getUUID(), packet.nonce()),
                ClientLauncherGuards.getDetectedReason()
        ));
    }
}

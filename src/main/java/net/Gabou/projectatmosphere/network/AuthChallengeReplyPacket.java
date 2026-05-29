package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.auth.ServerAuth;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AuthChallengeReplyPacket {
    private final long nonce;
    private final String response;
    private final String launcherReason;

    public AuthChallengeReplyPacket(long nonce, String response, String launcherReason) {
        this.nonce = nonce;
        this.response = response == null ? "" : response;
        this.launcherReason = launcherReason == null ? "" : launcherReason;
    }

    public AuthChallengeReplyPacket(FriendlyByteBuf buf) {
        this.nonce = buf.readLong();
        this.response = buf.readUtf(256);
        this.launcherReason = buf.readUtf(256);
    }

    public long nonce() {
        return nonce;
    }

    public String response() {
        return response;
    }

    public String launcherReason() {
        return launcherReason;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(nonce);
        buf.writeUtf(response, 256);
        buf.writeUtf(launcherReason, 256);
    }

    public static AuthChallengeReplyPacket decode(FriendlyByteBuf buf) {
        return new AuthChallengeReplyPacket(buf);
    }

    public static void handle(AuthChallengeReplyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerAuth.handleChallengeReply(player, msg);
            }
        });
        context.setPacketHandled(true);
    }
}

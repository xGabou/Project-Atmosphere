package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.auth.ServerAuth;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server authentication reply carrying the nonce response and launcher reason.
 */
public class AuthChallengeReplyPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "auth_challenge_reply");
    public static final Type<AuthChallengeReplyPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, AuthChallengeReplyPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), AuthChallengeReplyPacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuthChallengeReplyPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // IPayloadContext#player is the NeoForge replacement for Forge NetworkEvent.Context#getSender.
            if (ctx.player() instanceof ServerPlayer player) {
                ServerAuth.handleChallengeReply(player, msg);
            }
        });
    }
}

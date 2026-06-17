package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.auth.ClientAuth;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client authentication challenge carrying the nonce to validate.
 */
public class AuthChallengePacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "auth_challenge");
    public static final Type<AuthChallengePacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, AuthChallengePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), AuthChallengePacket::decode);

    private final long nonce;

    public AuthChallengePacket(long nonce) {
        this.nonce = nonce;
    }

    public AuthChallengePacket(FriendlyByteBuf buf) {
        this.nonce = buf.readLong();
    }

    public long nonce() {
        return nonce;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(nonce);
    }

    public static AuthChallengePacket decode(FriendlyByteBuf buf) {
        return new AuthChallengePacket(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuthChallengePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // NeoForge payload handlers replace Forge SimpleChannel handlers; this remains client-only auth state.
            if (FMLEnvironment.dist.isClient()) {
                ClientAuth.handleChallenge(msg);
            }
        });
    }
}

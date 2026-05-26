package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.auth.ClientAuth;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AuthChallengePacket {
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

    public static void handle(AuthChallengePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAuth.handleChallenge(msg)));
        context.setPacketHandled(true);
    }
}

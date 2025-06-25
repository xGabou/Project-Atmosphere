package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class SyncBiomeDataLoginPacket implements IntSupplier, Supplier<CompletableFuture<Void>> {

    private final String message;
    private int loginIndex;
    private NetworkEvent.Context context;  // Ajout pour accès plus tard

    public SyncBiomeDataLoginPacket(String message) {
        this.message = message;
    }

    public SyncBiomeDataLoginPacket(FriendlyByteBuf buf) {
        this.message = buf.readUtf();
        this.loginIndex = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.message);
        buf.writeVarInt(this.loginIndex);
    }

    public static CompletableFuture<Void> handle(SyncBiomeDataLoginPacket msg, NetworkEvent.Context ctx) {
        return ctx.enqueueWork(() -> {
            ClientSyncLock.setReadyForLocalPlayer(true);
            System.out.println("✅ Synchro biome terminée pour: " + Minecraft.getInstance().player.getName().getString());
            ctx.setPacketHandled(true);
        });
    }

    @Override
    public CompletableFuture<Void> get() {
        return CompletableFuture.runAsync(() -> {
            ClientSyncLock.setReadyForLocalPlayer(true);
            System.out.println("✅ Synchro biome terminée pour: " + Minecraft.getInstance().player.getName().getString());
        });
    }


    @Override
    public int getAsInt() {
        return loginIndex;
    }

    public void setLoginIndex(int index) {
        this.loginIndex = index;
    }

    // Setter pour que Forge puisse injecter le context
    public void setContext(NetworkEvent.Context ctx) {
        this.context = ctx;
    }
}


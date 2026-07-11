package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.modules.tornado.TornadoSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server-to-client packet carrying tornado snapshots.
 * It updates the client tornado cache and must not own tornado simulation logic.
 */
public class SyncTornadoesPacket {
    private final List<TornadoSnapshot> snapshots;

    public SyncTornadoesPacket(List<TornadoSnapshot> snapshots) {
        this.snapshots = List.copyOf(snapshots);
    }

    public SyncTornadoesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<TornadoSnapshot> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            read.add(TornadoSnapshot.read(buf));
        }
        this.snapshots = read;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.snapshots.size());
        for (TornadoSnapshot snapshot : this.snapshots) {
            snapshot.write(buf);
        }
    }

    // ---------------------------------------------------------------------
    // Decode and handle
    // ---------------------------------------------------------------------
    public static SyncTornadoesPacket decode(FriendlyByteBuf buf) {
        return new SyncTornadoesPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> SevereWeatherClientPacketHandlers.syncTornadoes(this.snapshots));
        ctx.get().setPacketHandled(true);
    }
}

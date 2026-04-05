package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncHurricanesPacket {
    private final List<HurricaneSnapshot> snapshots;

    public SyncHurricanesPacket(List<HurricaneSnapshot> snapshots) {
        this.snapshots = List.copyOf(snapshots);
    }

    public SyncHurricanesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<HurricaneSnapshot> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            read.add(HurricaneSnapshot.read(buf));
        }
        this.snapshots = read;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.snapshots.size());
        for (HurricaneSnapshot snapshot : this.snapshots) {
            snapshot.write(buf);
        }
    }

    public static SyncHurricanesPacket decode(FriendlyByteBuf buf) {
        return new SyncHurricanesPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> HurricaneManager.applyClientSnapshots(this.snapshots));
        ctx.get().setPacketHandled(true);
    }
}

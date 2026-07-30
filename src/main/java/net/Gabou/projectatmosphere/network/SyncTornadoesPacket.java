package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client packet carrying tornado snapshots.
 * It updates the client tornado cache and must not own tornado simulation logic.
 */
public class SyncTornadoesPacket implements CustomPacketPayload {
    public static final Type<SyncTornadoesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "sync_tornadoes")
    );
    public static final StreamCodec<FriendlyByteBuf, SyncTornadoesPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), SyncTornadoesPacket::decode);
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncTornadoesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SevereWeatherClientPacketHandlers.syncTornadoes(packet.snapshots));
    }
}

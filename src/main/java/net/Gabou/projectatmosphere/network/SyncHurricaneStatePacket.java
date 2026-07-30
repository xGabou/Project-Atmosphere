package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneRenderSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client packet carrying hurricane render snapshots.
 * It updates the client hurricane cache and must not own hurricane simulation logic.
 */
public class SyncHurricaneStatePacket implements CustomPacketPayload {
    public static final Type<SyncHurricaneStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "sync_hurricane_state")
    );
    public static final StreamCodec<FriendlyByteBuf, SyncHurricaneStatePacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), SyncHurricaneStatePacket::decode);
    private final List<HurricaneRenderSnapshot> snapshots;

    public SyncHurricaneStatePacket(List<HurricaneRenderSnapshot> snapshots) {
        this.snapshots = List.copyOf(snapshots);
    }

    public SyncHurricaneStatePacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<HurricaneRenderSnapshot> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            decoded.add(HurricaneRenderSnapshot.decode(buf));
        }
        this.snapshots = List.copyOf(decoded);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.snapshots.size());
        for (HurricaneRenderSnapshot snapshot : this.snapshots) {
            snapshot.encode(buf);
        }
    }

    // ---------------------------------------------------------------------
    // Decode and handle
    // ---------------------------------------------------------------------
    public static SyncHurricaneStatePacket decode(FriendlyByteBuf buf) {
        return new SyncHurricaneStatePacket(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncHurricaneStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SevereWeatherClientPacketHandlers.syncHurricanes(packet.snapshots));
    }
}

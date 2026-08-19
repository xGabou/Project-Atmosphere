package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.modules.hurricane.HurricaneRenderSnapshot;
import net.Gabou.projectatmosphere.platform.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client packet carrying hurricane render snapshots.
 * It updates the client hurricane cache and must not own hurricane simulation logic.
 */
public class SyncHurricaneStatePacket {
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

    public void handle(PacketContext context) {
        context.enqueueClient(() -> SevereWeatherClientPacketHandlers.syncHurricanes(this.snapshots));
        context.markHandled();
    }
}

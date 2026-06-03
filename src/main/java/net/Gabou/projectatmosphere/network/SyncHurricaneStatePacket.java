package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneRenderSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHurricaneStateCache.applySnapshots(this.snapshots))
        );
        ctx.get().setPacketHandled(true);
    }
}

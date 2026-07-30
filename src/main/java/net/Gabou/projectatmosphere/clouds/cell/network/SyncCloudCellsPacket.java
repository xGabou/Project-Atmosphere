package net.Gabou.projectatmosphere.clouds.cell.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.client.ClientCloudCellCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server-to-client full cloud cell snapshot. Sent on join/dimension change or
 * whenever the server decides the client's view has drifted too far; regular
 * updates use {@link CloudCellDeltaPacket}.
 */
public final class SyncCloudCellsPacket implements CustomPacketPayload {
    public static final Type<SyncCloudCellsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "sync_cloud_cells")
    );
    public static final StreamCodec<FriendlyByteBuf, SyncCloudCellsPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> packet.encode(buffer), SyncCloudCellsPacket::decode);
    private final List<CloudCell> cells;
    private final long worldTime;

    public SyncCloudCellsPacket(Collection<CloudCell> cells, long worldTime) {
        this.cells = cells == null ? List.of() : List.copyOf(cells);
        this.worldTime = worldTime;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarLong(worldTime);
        buffer.writeVarInt(cells.size());
        for (CloudCell cell : cells) {
            cell.encode(buffer);
        }
    }

    public static SyncCloudCellsPacket decode(FriendlyByteBuf buffer) {
        long worldTime = buffer.readVarLong();
        int count = buffer.readVarInt();
        List<CloudCell> cells = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cells.add(CloudCell.decode(buffer));
        }
        return new SyncCloudCellsPacket(cells, worldTime);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncCloudCellsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCloudCellCache.applyFullSnapshot(packet.cells, packet.worldTime));
    }
}

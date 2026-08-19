package net.Gabou.projectatmosphere.clouds.cell.network;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.client.ClientCloudCellCache;
import net.Gabou.projectatmosphere.platform.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server-to-client full cloud cell snapshot. Sent on join/dimension change or
 * whenever the server decides the client's view has drifted too far; regular
 * updates use {@link CloudCellDeltaPacket}.
 */
public final class SyncCloudCellsPacket {
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

    public static void handle(SyncCloudCellsPacket packet, PacketContext context) {
        context.enqueueClient(() -> ClientCloudCellCache.applyFullSnapshot(packet.cells, packet.worldTime));
        context.markHandled();
    }
}

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
import java.util.UUID;

/**
 * Server-to-client cloud cell delta: only cells that changed since the last
 * sync plus explicit removals. Keeps steady-state bandwidth proportional to
 * activity, not population.
 */
public final class CloudCellDeltaPacket implements CustomPacketPayload {
    public static final Type<CloudCellDeltaPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_cell_delta")
    );
    public static final StreamCodec<FriendlyByteBuf, CloudCellDeltaPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> packet.encode(buffer), CloudCellDeltaPacket::decode);
    private final List<CloudCell> updated;
    private final List<UUID> removed;
    private final long worldTime;

    public CloudCellDeltaPacket(Collection<CloudCell> updated, Collection<UUID> removed, long worldTime) {
        this.updated = updated == null ? List.of() : List.copyOf(updated);
        this.removed = removed == null ? List.of() : List.copyOf(removed);
        this.worldTime = worldTime;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarLong(worldTime);
        buffer.writeVarInt(updated.size());
        for (CloudCell cell : updated) {
            cell.encode(buffer);
        }
        buffer.writeVarInt(removed.size());
        for (UUID id : removed) {
            buffer.writeUUID(id);
        }
    }

    public static CloudCellDeltaPacket decode(FriendlyByteBuf buffer) {
        long worldTime = buffer.readVarLong();
        int updatedCount = buffer.readVarInt();
        List<CloudCell> updated = new ArrayList<>(updatedCount);
        for (int i = 0; i < updatedCount; i++) {
            updated.add(CloudCell.decode(buffer));
        }
        int removedCount = buffer.readVarInt();
        List<UUID> removed = new ArrayList<>(removedCount);
        for (int i = 0; i < removedCount; i++) {
            removed.add(buffer.readUUID());
        }
        return new CloudCellDeltaPacket(updated, removed, worldTime);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CloudCellDeltaPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCloudCellCache.applyDelta(packet.updated, packet.removed, packet.worldTime));
    }
}

package net.Gabou.projectatmosphere.clouds.field.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Player-interest delta for render-authoritative CloudField snapshots. */
public final class CloudFieldDeltaPacket implements CustomPacketPayload {
    public static final Type<CloudFieldDeltaPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_field_delta")
    );
    public static final StreamCodec<FriendlyByteBuf, CloudFieldDeltaPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> packet.encode(buffer), CloudFieldDeltaPacket::decode);
    private static final int VERSION = 5;

    private final List<CloudFieldSnapshot> updated;
    private final List<UUID> removed;

    public CloudFieldDeltaPacket(
            Collection<CloudFieldSnapshot> updated,
            Collection<UUID> removed
    ) {
        this.updated = updated == null ? List.of() : List.copyOf(updated);
        this.removed = removed == null ? List.of() : List.copyOf(removed);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(VERSION);
        buffer.writeVarInt(updated.size());
        for (CloudFieldSnapshot snapshot : updated) {
            SyncCloudFieldsPacket.encodeSnapshot(buffer, snapshot);
        }
        buffer.writeVarInt(removed.size());
        for (UUID id : removed) {
            buffer.writeUUID(id);
        }
    }

    public static CloudFieldDeltaPacket decode(FriendlyByteBuf buffer) {
        int version = buffer.readVarInt();
        int updatedCount = buffer.readVarInt();
        List<CloudFieldSnapshot> updated = new ArrayList<>(updatedCount);
        for (int i = 0; i < updatedCount; i++) {
            updated.add(SyncCloudFieldsPacket.decodeSnapshot(buffer, version));
        }
        int removedCount = buffer.readVarInt();
        List<UUID> removed = new ArrayList<>(removedCount);
        for (int i = 0; i < removedCount; i++) {
            removed.add(buffer.readUUID());
        }
        return new CloudFieldDeltaPacket(updated, removed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CloudFieldDeltaPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CloudFieldPacketDispatcher.handleClientDelta(packet.updated, packet.removed));
    }
}

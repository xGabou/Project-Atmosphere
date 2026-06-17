package net.Gabou.projectatmosphere.clouds.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server-to-client packet for Project Atmosphere cloud render regions.
 */
public final class SyncCloudRegionsPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "sync_cloud_regions");
    public static final Type<SyncCloudRegionsPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SyncCloudRegionsPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), SyncCloudRegionsPacket::decode);

    private final List<CloudRegionRenderData> regions;

    public SyncCloudRegionsPacket(Collection<CloudRegionRenderData> regions) {
        this.regions = regions != null ? List.copyOf(regions) : List.of();
    }

    public SyncCloudRegionsPacket(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<CloudRegionRenderData> decodedRegions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            decodedRegions.add(CloudRegionRenderData.decode(buffer));
        }
        this.regions = List.copyOf(decodedRegions);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(regions.size());
        for (CloudRegionRenderData region : regions) {
            region.encode(buffer);
        }
    }

    public static SyncCloudRegionsPacket decode(FriendlyByteBuf buffer) {
        return new SyncCloudRegionsPacket(buffer);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncCloudRegionsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                CloudRegionPacketDispatcher.handleClientRegions(packet.regions);
            }
        });
    }
}

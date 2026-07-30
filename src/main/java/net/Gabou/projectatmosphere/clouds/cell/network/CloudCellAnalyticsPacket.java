package net.Gabou.projectatmosphere.clouds.cell.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.analytics.CloudCellAnalyticsReport;
import net.Gabou.projectatmosphere.clouds.cell.sim.CloudCellSimulationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Client-to-server GPU analytics digest. The server treats these as advisory
 * evidence only (rate-limited, sanity-checked); a malicious or broken client
 * can at worst nudge merge timing, never spawn or teleport cells.
 */
public final class CloudCellAnalyticsPacket implements CustomPacketPayload {
    public static final Type<CloudCellAnalyticsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_cell_analytics")
    );
    public static final StreamCodec<FriendlyByteBuf, CloudCellAnalyticsPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> packet.encode(buffer), CloudCellAnalyticsPacket::decode);
    private static final int MAX_REPORTS = 128;

    private final List<CloudCellAnalyticsReport> reports;

    public CloudCellAnalyticsPacket(Collection<CloudCellAnalyticsReport> reports) {
        this.reports = reports == null ? List.of() : List.copyOf(reports);
    }

    public void encode(FriendlyByteBuf buffer) {
        int count = Math.min(reports.size(), MAX_REPORTS);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            reports.get(i).encode(buffer);
        }
    }

    public static CloudCellAnalyticsPacket decode(FriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_REPORTS);
        List<CloudCellAnalyticsReport> reports = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            reports.add(CloudCellAnalyticsReport.decode(buffer));
        }
        return new CloudCellAnalyticsPacket(reports);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CloudCellAnalyticsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                CloudCellSimulationManager.getInstance().acceptAnalytics(sender, packet.reports);
            }
        });
    }
}

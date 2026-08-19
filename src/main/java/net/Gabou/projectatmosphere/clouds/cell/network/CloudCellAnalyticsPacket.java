package net.Gabou.projectatmosphere.clouds.cell.network;

import net.Gabou.projectatmosphere.clouds.analytics.CloudCellAnalyticsReport;
import net.Gabou.projectatmosphere.clouds.cell.sim.CloudCellSimulationManager;
import net.Gabou.projectatmosphere.platform.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Client-to-server GPU analytics digest. The server treats these as advisory
 * evidence only (rate-limited, sanity-checked); a malicious or broken client
 * can at worst nudge merge timing, never spawn or teleport cells.
 */
public final class CloudCellAnalyticsPacket {
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

    public static void handle(CloudCellAnalyticsPacket packet, PacketContext context) {
        context.enqueue(() -> {
            ServerPlayer sender = context.sender();
            if (sender != null) {
                CloudCellSimulationManager.getInstance().acceptAnalytics(sender, packet.reports);
            }
        });
        context.markHandled();
    }
}

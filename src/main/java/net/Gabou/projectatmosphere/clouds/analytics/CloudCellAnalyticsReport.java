package net.Gabou.projectatmosphere.clouds.analytics;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * One GPU-measured cell analytics digest. Compact enough to ship to the
 * server, where it is treated as advisory evidence (never authority) for
 * merge/split and classification decisions.
 */
public record CloudCellAnalyticsReport(
        UUID cellId,
        float integratedCoverage,
        float footprintAreaRatio,
        float centroidDriftX,
        float centroidDriftZ,
        float maxTop01,
        UUID bestOverlapPeer,
        float bestOverlapScore,
        float splitScore
) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(cellId);
        buffer.writeFloat(integratedCoverage);
        buffer.writeFloat(footprintAreaRatio);
        buffer.writeFloat(centroidDriftX);
        buffer.writeFloat(centroidDriftZ);
        buffer.writeFloat(maxTop01);
        buffer.writeBoolean(bestOverlapPeer != null);
        if (bestOverlapPeer != null) {
            buffer.writeUUID(bestOverlapPeer);
        }
        buffer.writeFloat(bestOverlapScore);
        buffer.writeFloat(splitScore);
    }

    public static CloudCellAnalyticsReport decode(FriendlyByteBuf buffer) {
        UUID cellId = buffer.readUUID();
        float integratedCoverage = buffer.readFloat();
        float footprintAreaRatio = buffer.readFloat();
        float centroidDriftX = buffer.readFloat();
        float centroidDriftZ = buffer.readFloat();
        float maxTop01 = buffer.readFloat();
        UUID peer = buffer.readBoolean() ? buffer.readUUID() : null;
        float bestOverlapScore = buffer.readFloat();
        float splitScore = buffer.readFloat();
        return new CloudCellAnalyticsReport(
                cellId, integratedCoverage, footprintAreaRatio,
                centroidDriftX, centroidDriftZ, maxTop01,
                peer, bestOverlapScore, splitScore
        );
    }
}

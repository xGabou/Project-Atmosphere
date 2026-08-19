package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.RegionIdCodec;
import net.Gabou.projectatmosphere.platform.network.PacketContext;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server-to-client packet carrying the current wind state for a region.
 */
public class SyncWindPacket {
    private final RegionInstanceKey regionId;
    private final float baseSpeed;
    private final float gustSpeed;
    private final float directionDeg;
    private final long gustEndTick;

    public SyncWindPacket(RegionInstanceKey regionId, float baseSpeed, float gustSpeed, float directionDeg, long gustEndTick) {
        this.regionId = regionId;
        this.baseSpeed = baseSpeed;
        this.gustSpeed = gustSpeed;
        this.directionDeg = directionDeg;
        this.gustEndTick = gustEndTick;
    }

    public SyncWindPacket(FriendlyByteBuf buf) {
        this.regionId = RegionIdCodec.read(buf);
        this.baseSpeed = buf.readFloat();
        this.gustSpeed = buf.readFloat();
        this.directionDeg = buf.readFloat();
        this.gustEndTick = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        RegionIdCodec.write(buf, regionId);
        buf.writeFloat(baseSpeed);
        buf.writeFloat(gustSpeed);
        buf.writeFloat(directionDeg);
        buf.writeLong(gustEndTick);
    }

    public static SyncWindPacket decode(FriendlyByteBuf buf) {
        return new SyncWindPacket(buf);
    }

    public void handle(PacketContext context) {
        context.enqueueClient(() -> WindVector.set(regionId, baseSpeed + gustSpeed, directionDeg));
        context.markHandled();
    }
}

package net.Gabou.projectatmosphere.network;

import java.util.function.Supplier;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegionId;
import net.Gabou.projectatmosphere.modules.region.RegionIdCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class SyncWindPacket {
    private final ForecastRegionId regionId;
    private final float baseSpeed;
    private final float gustSpeed;
    private final float directionDeg;
    private final long gustEndTick;

    public SyncWindPacket(ForecastRegionId regionId, float baseSpeed, float gustSpeed, float directionDeg, long gustEndTick) {
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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client-side wind cache can be keyed by region id; fallback to legacy RegionInstanceKey if needed.
            WindVector.set(new net.Gabou.projectatmosphere.util.RegionInstanceKey(regionId.rx(), regionId.rz(), net.Gabou.projectatmosphere.util.RegionInstanceKey.DEFAULT_REGION_SIZE),
                    baseSpeed + gustSpeed, directionDeg);
        });
        ctx.get().setPacketHandled(true);
    }
}


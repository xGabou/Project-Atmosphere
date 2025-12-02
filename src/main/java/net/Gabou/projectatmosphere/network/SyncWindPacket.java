package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncWindPacket {
    private final BiomeInstanceKey key;
    private final float baseSpeed;
    private final float gustSpeed;
    private final float directionDeg;
    private final long gustEndTick;

    public SyncWindPacket(BiomeInstanceKey key, float baseSpeed, float gustSpeed, float directionDeg, long gustEndTick) {
        this.key = key;
        this.baseSpeed = baseSpeed;
        this.gustSpeed = gustSpeed;
        this.directionDeg = directionDeg;
        this.gustEndTick = gustEndTick;
    }

    public SyncWindPacket(FriendlyByteBuf buf) {
        this.key = BiomeInstanceKey.fromString(buf.readUtf());
        this.baseSpeed = buf.readFloat();
        this.gustSpeed = buf.readFloat();
        this.directionDeg = buf.readFloat();
        this.gustEndTick = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(key.toString());
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
            RegionInstanceKey regionKey = RegionInstanceKey.from(key.samplePos());
            WindVector.set(regionKey, baseSpeed + gustSpeed, directionDeg);
        });
        ctx.get().setPacketHandled(true);
    }
}


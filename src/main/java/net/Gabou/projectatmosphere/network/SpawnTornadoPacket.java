package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SpawnTornadoPacket {
    private final Vec3 pos;
    private final float radius;
    private final float speed;
    private final float angle;

    public SpawnTornadoPacket(Vec3 pos, float radius, WindVector wind) {
        this.pos = pos;
        this.radius = radius;
        this.speed = wind.speed();
        this.angle = wind.angleRadians();
    }

    public SpawnTornadoPacket(FriendlyByteBuf buf) {
        this.pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.radius = buf.readFloat();
        this.speed = buf.readFloat();
        this.angle = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeFloat(radius);
        buf.writeFloat(speed);
        buf.writeFloat(angle);
    }

    public static SpawnTornadoPacket decode(FriendlyByteBuf buf) {
        return new SpawnTornadoPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            TornadoManager.spawn(pos, radius, new WindVector(speed, angle));
        });
        ctx.get().setPacketHandled(true);
    }
}


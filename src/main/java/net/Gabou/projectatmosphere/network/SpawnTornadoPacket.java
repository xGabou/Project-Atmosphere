package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server-to-client packet spawning a tornado in the client cache.
 * It transports spawn data only and must not own tornado simulation or render policy.
 */
public class SpawnTornadoPacket implements CustomPacketPayload {
    public static final Type<SpawnTornadoPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "spawn_tornado")
    );
    public static final StreamCodec<FriendlyByteBuf, SpawnTornadoPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), SpawnTornadoPacket::decode);
    private final UUID id;
    private final Vec3 pos;
    private final float radius;
    private final float bottomY;
    private final float height;
    private final float speed;
    private final float angle;
    private final float gust;

    public SpawnTornadoPacket(UUID id, Vec3 pos, float radius, WindVector wind, float bottomY, float height) {
        this.id = id;
        this.pos = pos;
        this.radius = radius;
        this.bottomY = bottomY;
        this.height = height;
        this.speed = wind.baseSpeed();
        this.angle = wind.angleRadians();
        this.gust = wind.gustSpeed();
    }

    public SpawnTornadoPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.radius = buf.readFloat();
        this.bottomY = buf.readFloat();
        this.height = buf.readFloat();
        this.speed = buf.readFloat();
        this.angle = buf.readFloat();
        this.gust = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeFloat(radius);
        buf.writeFloat(bottomY);
        buf.writeFloat(height);
        buf.writeFloat(speed);
        buf.writeFloat(angle);
        buf.writeFloat(gust);
    }

    // ---------------------------------------------------------------------
    // Decode and handle
    // ---------------------------------------------------------------------
    public static SpawnTornadoPacket decode(FriendlyByteBuf buf) {
        return new SpawnTornadoPacket(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpawnTornadoPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SevereWeatherClientPacketHandlers.spawnTornado(
                    new SevereWeatherClientPacketHandlers.TornadoSpawn(
                            packet.id,
                            packet.pos,
                            packet.radius,
                            new WindVector(packet.speed, packet.angle, packet.gust),
                            packet.bottomY,
                            packet.height
                    )
            );
        });
    }
}

package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpawnTornadoPacket(Vec3 pos, float radius, float speed, float angle, float gust)
        implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "spawn_tornado");

    public static final Type<SpawnTornadoPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SpawnTornadoPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeDouble(pkt.pos.x);
                        buf.writeDouble(pkt.pos.y);
                        buf.writeDouble(pkt.pos.z);
                        buf.writeFloat(pkt.radius);
                        buf.writeFloat(pkt.speed);
                        buf.writeFloat(pkt.angle);
                        buf.writeFloat(pkt.gust);
                    },
                    buf -> new SpawnTornadoPacket(
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpawnTornadoPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> TornadoManager.spawnClient(
                pkt.pos(),
                pkt.radius(),
                new WindVector(pkt.speed(), pkt.angle(), pkt.gust())
        ));
    }
}

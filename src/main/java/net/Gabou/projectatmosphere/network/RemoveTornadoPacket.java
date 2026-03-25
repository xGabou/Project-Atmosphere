package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class RemoveTornadoPacket {
    private final UUID id;

    public RemoveTornadoPacket(UUID id) {
        this.id = id;
    }

    public RemoveTornadoPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
    }

    public static RemoveTornadoPacket decode(FriendlyByteBuf buf) {
        return new RemoveTornadoPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ProjectAtmosphere.DEBUG_MODE) {
                ProjectAtmosphere.LOGGER.info("[TornadoDebug] Client received RemoveTornadoPacket id={}", this.id);
            }
            TornadoManager.removeClientTornado(this.id);
        });
        ctx.get().setPacketHandled(true);
    }
}

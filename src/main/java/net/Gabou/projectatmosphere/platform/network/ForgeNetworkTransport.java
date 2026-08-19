package net.Gabou.projectatmosphere.platform.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Objects;

/** Forge 1.20.1 implementation of the outbound transport port. */
public final class ForgeNetworkTransport implements NetworkTransport {
    private final SimpleChannel channel;

    public ForgeNetworkTransport(SimpleChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object message) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    @Override
    public void sendToAll(Object message) {
        channel.send(PacketDistributor.ALL.noArg(), message);
    }

    @Override
    public void sendToDimension(ServerLevel level, Object message) {
        channel.send(PacketDistributor.DIMENSION.with(level::dimension), message);
    }

    @Override
    public void sendToServer(Object message) {
        channel.sendToServer(message);
    }
}

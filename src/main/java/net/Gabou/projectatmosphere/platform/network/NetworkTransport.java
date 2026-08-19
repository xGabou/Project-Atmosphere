package net.Gabou.projectatmosphere.platform.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-neutral outbound network operations used by atmosphere services.
 * Packet registration and routing details belong to the active loader
 * adapter, not to simulation or orchestration code.
 */
public interface NetworkTransport {
    void sendToPlayer(ServerPlayer player, Object message);

    void sendToAll(Object message);

    void sendToDimension(ServerLevel level, Object message);

    void sendToServer(Object message);
}

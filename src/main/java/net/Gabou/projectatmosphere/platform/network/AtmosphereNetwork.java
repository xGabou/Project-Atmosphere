package net.Gabou.projectatmosphere.platform.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Composition point for the loader-specific network adapter. */
public final class AtmosphereNetwork {
    private static volatile NetworkTransport transport;

    private AtmosphereNetwork() {
    }

    public static void install(NetworkTransport installedTransport) {
        transport = Objects.requireNonNull(installedTransport, "installedTransport");
    }

    public static NetworkTransport transport() {
        NetworkTransport current = transport;
        if (current == null) {
            throw new IllegalStateException("Atmosphere network transport has not been installed");
        }
        return current;
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        transport().sendToPlayer(player, message);
    }

    public static void sendToAll(Object message) {
        transport().sendToAll(message);
    }

    public static void sendToDimension(ServerLevel level, Object message) {
        transport().sendToDimension(level, message);
    }

    public static void sendToServer(Object message) {
        transport().sendToServer(message);
    }
}

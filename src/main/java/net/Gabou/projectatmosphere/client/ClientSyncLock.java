package net.Gabou.projectatmosphere.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.UUID;

public class ClientSyncLock {
    private static volatile boolean ready;

    public static boolean isReady() {
        return ready;
    }

    public static boolean isPlayerReady(UUID playerUUID) {
        return ready;
    }

    public static void setReady(UUID playerUUID, boolean ready) {
        ClientSyncLock.ready = ready;
    }

    public static void setReadyForLocalPlayer(boolean ready) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            setReady(player.getUUID(), ready);
            return;
        }
        ClientSyncLock.ready = ready;
    }

    public static void clear() {
        ready = false;
    }
}

package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientSyncLock {

    public static boolean isReady() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        return isPlayerReady(player.getUUID());
    }

    public static boolean isPlayerReady(UUID playerUUID) {
        return AtmosphereManager.isPlayerReady(playerUUID);
    }

    public static void setReady(UUID playerUUID, boolean ready) {
        if(ProjectAtmosphere.DEBUG_MODE)
            ProjectAtmosphere.LOGGER.info("Setting player ready to " + playerUUID);
    }

    public static void setReadyForLocalPlayer(boolean ready) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            setReady(player.getUUID(), ready);
        }
    }

    public static void clear() {
        
    }
}

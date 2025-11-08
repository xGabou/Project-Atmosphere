package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.minecraftforge.event.TickEvent;

public class TickCounter {
    private static int currentTick = 0;

    public static void onServerTick() {
            currentTick++;
            DelayedTaskScheduler.tick(currentTick);

    }

    public static int getCurrentTick() {
        return currentTick;
    }
}

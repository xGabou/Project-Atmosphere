package net.Gabou.projectatmosphere.util;


import net.neoforged.neoforge.event.tick.ServerTickEvent;



public class TickCounter {
    private static int currentTick = 0;

    public static void onServerTick(ServerTickEvent.Post event) {
        currentTick++;
        DelayedTaskScheduler.tick(currentTick);
    }

    public static int getCurrentTick() {
        return currentTick;
    }
}

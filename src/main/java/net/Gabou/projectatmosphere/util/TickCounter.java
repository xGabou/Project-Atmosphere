package net.Gabou.projectatmosphere.util;
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

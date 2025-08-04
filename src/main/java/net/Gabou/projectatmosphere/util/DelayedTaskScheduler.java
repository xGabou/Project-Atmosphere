package net.Gabou.projectatmosphere.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DelayedTaskScheduler {
    private static final Map<Integer, List<Runnable>> scheduledTasks = new HashMap<>();

    public static void schedule(int ticksDelay, Runnable task) {
        int targetTick = TickCounter.getCurrentTick() + ticksDelay;
        scheduledTasks.computeIfAbsent(targetTick, k -> new ArrayList<>()).add(task);
    }

    public static void tick(int currentTick) {
        List<Runnable> tasks = scheduledTasks.remove(currentTick);
        if (tasks != null) {
            tasks.forEach(Runnable::run);
        }
    }
}

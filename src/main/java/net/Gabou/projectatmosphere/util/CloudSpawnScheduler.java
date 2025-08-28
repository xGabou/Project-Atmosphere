package net.Gabou.projectatmosphere.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber
public class CloudSpawnScheduler {
    private static final List<ScheduledSpawn> tasks = new ArrayList<>();

    public static void schedule(String cloudId, ServerLevel level, Runnable task, int delayTicks) {
        tasks.add(new ScheduledSpawn(cloudId, level, task, delayTicks));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<ScheduledSpawn> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            ScheduledSpawn s = iterator.next();
            s.ticksLeft--;
            if (s.ticksLeft <= 0) {
                s.task.run();
                iterator.remove();
            }
        }
    }

    private static class ScheduledSpawn {
        final String cloudId;
        final ServerLevel level;
        final Runnable task;
        int ticksLeft;

        ScheduledSpawn(String cloudId, ServerLevel level, Runnable task, int ticksLeft) {
            this.cloudId = cloudId;
            this.level = level;
            this.task = task;
            this.ticksLeft = ticksLeft;
        }
    }
}

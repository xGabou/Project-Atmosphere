package net.Gabou.projectatmosphere.temperature.event;

import net.Gabou.projectatmosphere.temperature.TemperatureManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.Gabou.projectatmosphere.temperature.util.*;

@Mod.EventBusSubscriber
public class TemperatureTickHandler {

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        // e.g. every 10 minutes (6000 ticks) update profiles if needed
        long t = event.level.getDayTime() % 24000;
        if (t == 0) {
            // midnight reached: schedule next day profile re-gen
            TemperatureManager.onMidnight(event.level);
        }
    }
}

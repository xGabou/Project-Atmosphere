package net.Gabou.projectatmosphere.temperature.event;

import net.Gabou.projectatmosphere.temperature.TemperatureManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.Gabou.projectatmosphere.temperature.util.*;

@Mod.EventBusSubscriber
public class TemperatureTickHandler {

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.level instanceof ServerLevel world)) return;

        long t = world.getDayTime() % 24000;

        if (t == 21000) { // 3:00 AM
            TemperatureManager.onMidnight(event.level);
        }
    }
}

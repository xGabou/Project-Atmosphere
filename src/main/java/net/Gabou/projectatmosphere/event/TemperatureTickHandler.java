package net.Gabou.projectatmosphere.event;


import net.Gabou.projectatmosphere.manager.AtmosphereManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class TemperatureTickHandler {
    private static final int RUN_INTERVAL_TICKS = 6000;

    //@SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.level.isClientSide) return;
        long t = event.level.getDayTime() % 24000L;

        if (t == 21000L) {
            AtmosphereManager.onSwapProfiles((ServerLevel) event.level);
        }

        if (t % RUN_INTERVAL_TICKS == 0) {
            AtmosphereManager.tick((ServerLevel) event.level);
        }
    }
}

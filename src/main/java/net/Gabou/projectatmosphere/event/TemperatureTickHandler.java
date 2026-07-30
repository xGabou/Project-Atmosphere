package net.Gabou.projectatmosphere.event;


import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber
public class TemperatureTickHandler {
    private static final int RUN_INTERVAL_TICKS = 6000;

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Pre event) {
        if (event.getLevel().isClientSide) return;
        long t = event.getLevel().getDayTime() % 24000L;

        if (t == 21000L) {
            AtmosphereManager.onSwapProfiles((ServerLevel) event.getLevel());
        }

    }
}

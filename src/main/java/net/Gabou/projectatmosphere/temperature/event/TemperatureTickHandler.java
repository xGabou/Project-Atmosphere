package net.Gabou.projectatmosphere.temperature.event;


import net.Gabou.projectatmosphere.temperature.TemperatureManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class TemperatureTickHandler {

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.level.isClientSide) return;
        long t = event.level.getDayTime() % 24000L;

        if (t == 21000L) {
            TemperatureManager.onMidnight(event.level);
        } else if (t == 9000L) {
            TemperatureManager.onPeakDay(event.level);
        }
    }
}

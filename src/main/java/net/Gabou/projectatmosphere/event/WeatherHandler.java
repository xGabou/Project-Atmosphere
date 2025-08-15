package net.Gabou.projectatmosphere.event;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class WeatherHandler {

    @SubscribeEvent
    public static void onWeatherTick(TickEvent.LevelTickEvent event) {
        if (event.level instanceof ServerLevel level && event.phase == TickEvent.Phase.END) {
            SnowstormManager.tick(level);
        }
    }
}

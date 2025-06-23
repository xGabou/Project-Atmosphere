package net.Gabou.projectatmosphere.event;

import net.Gabou.projectatmosphere.manager.CloudSpawner;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.Gabou.projectatmosphere.ProjectAtmosphere;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class EventHandler {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!event.level.isClientSide && event.phase == TickEvent.Phase.END) {
            if (event.level instanceof ServerLevel serverLevel) {
                SimpleCloudSpawner.trySpawnClouds(serverLevel);
            }
        }
    }
}

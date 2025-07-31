package net.Gabou.projectatmosphere.event;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.ScAPICloudRegion;
import dev.nonamecrackers2.simpleclouds.api.common.event.*;
import dev.nonamecrackers2.simpleclouds.api.common.world.ScAPICloudManager;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.Gabou.projectatmosphere.ProjectAtmosphere;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class SimpleCloudsEventListener {

    public static void onCloudRegionSpawn(CloudRegionNaturallySpawnEvent event) {
        Level level = event.getLevel();
        ScAPICloudRegion region = event.getCloudRegion();
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud region spawned naturally at {}, {}", region.getPosX(), region.getPosZ());
    }

    public static void onCloudRegionRemoved(CloudRegionRemovedEvent event) {
        if(event.getLevel()==null)
            return;
        if((event.getLevel().isClientSide))
            return;
        ScAPICloudRegion region = event.getCloudRegion();
        CloudRegionRemovedEvent.Reason reason = event.getReason();

        if (reason == CloudRegionRemovedEvent.Reason.MANUALLY) {
            SimpleCloudsCompat.doInitialGenWithWeather((int) region.getPosX(),(int) region.getPosZ(), (ServerLevel) event.getLevel());
        } else {
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud region removed for reason: {}", reason);
        }
    }


    public static void onCloudRegionTick(CloudRegionTickEvent event) {
        ScAPICloudRegion region = event.getCloudRegion();
    }


    public static void onModifyCloudSpeed(ModifyCloudSpeedEvent event) {
    }
}

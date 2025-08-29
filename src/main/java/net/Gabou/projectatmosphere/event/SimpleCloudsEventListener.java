package net.Gabou.projectatmosphere.event;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.ScAPICloudRegion;
import dev.nonamecrackers2.simpleclouds.api.common.event.*;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.Gabou.projectatmosphere.ProjectAtmosphere;

public class SimpleCloudsEventListener {

    // Register all listeners on the given event bus
    public static void register(IEventBus bus) {
        bus.addListener(SimpleCloudsEventListener::onCloudRegionSpawn);
        bus.addListener(SimpleCloudsEventListener::onCloudRegionRemoved);
        bus.addListener(SimpleCloudsEventListener::onCloudRegionTick);
        bus.addListener(SimpleCloudsEventListener::onModifyCloudSpeed);
    }

    public static void onCloudRegionSpawn(CloudRegionNaturallySpawnEvent event) {
        Level level = event.getLevel();
        ScAPICloudRegion region = event.getCloudRegion();
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud region spawned naturally at {}, {}", region.getWorldX(), region.getWorldZ());
    }

    public static void onCloudRegionRemoved(CloudRegionRemovedEvent event) {
        if (event.getLevel() == null || event.getLevel().isClientSide) {
            return;
        }
        ScAPICloudRegion region = event.getCloudRegion();
        CloudRegionRemovedEvent.Reason reason = event.getReason();

        if (reason == CloudRegionRemovedEvent.Reason.MANUALLY) {
            SimpleCloudsCompat.doInitialGenWithWeather(
                    (int) region.getWorldX(),
                    (int) region.getWorldZ(),
                    (ServerLevel) event.getLevel()
            );
        } else {
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud region removed for reason: {}", reason);
        }
    }

    public static void onCloudRegionTick(CloudRegionTickEvent event) {
        ScAPICloudRegion region = event.getCloudRegion();
        // Do something with the region per tick if needed
    }

    public static void onModifyCloudSpeed(ModifyCloudSpeedEvent event) {
        // Can adjust cloud speed if needed
    }
}

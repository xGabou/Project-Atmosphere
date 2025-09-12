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
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud region spawned naturally at {}, {}", region.getWorldX(), region.getWorldZ());
    }

    public static void onCloudRegionRemoved(CloudRegionRemovedEvent event) {
        if(event.getLevel()==null)
            return;
        if((event.getLevel().isClientSide))
            return;
        ScAPICloudRegion region = event.getCloudRegion();
        CloudRegionRemovedEvent.Reason reason = event.getReason();

        if (reason == CloudRegionRemovedEvent.Reason.MANUALLY) {
            try {
                SimpleCloudsCompat.doInitialGenWithWeather((int) region.getWorldX(), (int) region.getWorldZ(), (ServerLevel) event.getLevel());
            }
            catch (Exception e) {
                ProjectAtmosphere.LOGGER.error("[Atmosphere] Error during cloud region regeneration at {}, {}", region.getWorldX(), region.getWorldZ(), e);
                SimpleCloudsCompat.setIsInit(true);
            }
        } else {
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud region removed for reason: {}", reason);
        }
    }


    @SubscribeEvent
    public static void onCloudRegionTick(CloudRegionTickEvent event) {
        if (event.getLevel() == null || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ScAPICloudRegion region = event.getCloudRegion();

        // Determine a representative biome key at the region center
        int x = (int) region.getWorldX();
        int z = (int) region.getWorldZ();
        int y = serverLevel.getSeaLevel();
        net.Gabou.projectatmosphere.util.BiomeInstanceKey key =
                net.Gabou.projectatmosphere.util.AtmosphereUtils.getBiomeKey(serverLevel, new net.minecraft.core.BlockPos(x, y, z));

        // Current sample (fallback-safe) for direction preservation
        var current = net.Gabou.projectatmosphere.modules.core.WindVector.getOrFallback(key, serverLevel);
        float currentSpeed = current.speedMps();
        float dirDeg = current.directionDeg();

        // Storm-based boost
        float chance = net.Gabou.projectatmosphere.manager.ForecastOrchestrator.getCurrentStormChance(key, serverLevel.getGameTime());
        if (chance > 0.2f) {
            // Scale boost with storm chance; cap to avoid absurd values
            float stormBoost = Math.min(12.0f, 3.0f + chance * 12.0f); // 3..15 m/s
            float boosted = Math.max(currentSpeed, stormBoost);

            // Extra amplification if a tornado is active in this cloud region vicinity
            float tornadoBoost = 0f;
            for (var t : net.Gabou.projectatmosphere.modules.tornado.TornadoManager.getActiveTornadoes()) {
                var cr = t.getCloudRegion();
                // Compare by proximity of cloud region centers
                double dx = cr.getWorldX() - region.getWorldX();
                double dz = cr.getWorldZ() - region.getWorldZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= (double) (t.radius + 150f)) { // generous overlap threshold
                    // Amplify based on tornado level
                    tornadoBoost = Math.max(tornadoBoost, (float) (8.0f + t.getLevel().getMaxWindSpeed() * 0.5f));
                }
            }

            float finalSpeed = Math.min(30.0f, boosted + tornadoBoost);
            net.Gabou.projectatmosphere.modules.core.WindVector.set(key, finalSpeed, dirDeg);
        }
    }


    public static void onModifyCloudSpeed(ModifyCloudSpeedEvent event) {
    }
}

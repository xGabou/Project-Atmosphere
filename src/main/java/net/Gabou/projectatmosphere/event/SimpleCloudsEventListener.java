package net.Gabou.projectatmosphere.event;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.ScAPICloudRegion;
import dev.nonamecrackers2.simpleclouds.api.common.event.CloudRegionNaturallySpawnEvent;
import dev.nonamecrackers2.simpleclouds.api.common.event.CloudRegionRemovedEvent;
import dev.nonamecrackers2.simpleclouds.api.common.event.CloudRegionTickEvent;
import dev.nonamecrackers2.simpleclouds.api.common.event.ModifyCloudSpeedEvent;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.wind.WindEngine;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class SimpleCloudsEventListener {

    @SubscribeEvent
    public static void onCloudRegionSpawn(CloudRegionNaturallySpawnEvent event) {
       if(!(event.getLevel() instanceof ServerLevel serverLevel)) {
           return;
       }
        CloudRegion region =(CloudRegion) event.getCloudRegion();
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud region spawned naturally at {}, {}", region.getWorldX(), region.getWorldZ());
        AtmosphereManager.queueAddCloudRegion(region);
    }

    @SubscribeEvent
    public static void onCloudRegionRemoved(CloudRegionRemovedEvent event) {

    }


    @SubscribeEvent
    public static void onCloudRegionTick(CloudRegionTickEvent event) {


        if ((event.getLevel() == null || event.getLevel().isClientSide)||!SimpleCloudsCompat.getIsInit())
            return;

        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        ScAPICloudRegion region = event.getCloudRegion();

        // Determine a representative biome key at the region center
        int x = (int) region.getWorldX();
        int z = (int) region.getWorldZ();
        int y = serverLevel.getSeaLevel();

        BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(serverLevel, new BlockPos(x, y, z));
        RegionInstanceKey regionKey = RegionInstanceKey.from(key.samplePos());
        WindVector current = WindEngine.getCurrentHighWindVector(key, serverLevel.getGameTime());
        float currentSpeed = current.baseSpeed();
        float dirDeg = (float) Math.toDegrees(current.angleRadians());

        // Storm-based boost
        float stormFactor = ForecastOrchestrator.getCurrentStormChance(key, serverLevel.getGameTime());
        if (stormFactor > 0.15f) {
            // Scale boost with storm activity; cap to avoid absurd values
            float finalSpeed = getFinalSpeed(stormFactor, currentSpeed, region);
            WindVector.set(regionKey, finalSpeed, dirDeg);
        }
    }

    private static float getFinalSpeed(float stormFactor, float currentSpeed, ScAPICloudRegion region) {
        float stormBoost = Math.min(12.0f, 3.0f + stormFactor * 12.0f); // 3..15 m/s
        float boosted = Math.max(currentSpeed, stormBoost);

        // Extra amplification if a tornado is active in this cloud region vicinity
        float tornadoBoost = 0f;
        for (var t : TornadoManager.getActiveTornadoes()) {
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
        return finalSpeed;
    }


    @SubscribeEvent
    public static void onModifyCloudSpeed(ModifyCloudSpeedEvent event) {
    }
}

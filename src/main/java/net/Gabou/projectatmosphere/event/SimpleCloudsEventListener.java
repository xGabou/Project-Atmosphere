package net.Gabou.projectatmosphere.event;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.ScAPICloudRegion;
import dev.nonamecrackers2.simpleclouds.api.common.event.*;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
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
        if(!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ScAPICloudRegion region = event.getCloudRegion();
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud region spawned naturally at {}, {}", region.getWorldX(), region.getWorldZ());
    }

    public static void onCloudRegionRemoved(CloudRegionRemovedEvent event) {
        if (event.getLevel() == null)
            return;

        ScAPICloudRegion region = event.getCloudRegion();
        CloudRegionRemovedEvent.Reason reason = event.getReason();

        if (reason == CloudRegionRemovedEvent.Reason.MANUALLY) {
            try {
                SimpleCloudsCompat.doInitialGenWithWeather((int) region.getWorldX(), (int) region.getWorldZ(), (ServerLevel) event.getLevel());
            } catch (Exception e) {
                ProjectAtmosphere.LOGGER.error("[Atmosphere] Error during cloud region regeneration at {}, {}", region.getWorldX(), region.getWorldZ(), e);
                SimpleCloudsCompat.setIsInit(true);
            }
        } else {
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud region removed for reason: {}", reason);
        }
    }

    public static void onCloudRegionTick(CloudRegionTickEvent event) {

        if ((event.getLevel() == null || event.getLevel().isClientSide) || !SimpleCloudsCompat.getIsInit())
            return;

        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        ScAPICloudRegion region = event.getCloudRegion();

        // Determine a representative biome key at the region center
        int x = (int) region.getWorldX();
        int z = (int) region.getWorldZ();
        int y = serverLevel.getSeaLevel();

        BiomeForecast forecast = ForecastGenerator.getClosestValidForecast(AtmosphereUtils.getBiomeKey(serverLevel, new BlockPos(x, y, z)), ForecastType.STORM);
        if (forecast == null) {
            return;
        }
        BiomeInstanceKey key = forecast.getBiomeKey();
        // Current sample (fallback-safe) for direction preservation
        var current = WindVector.getOrFallback(key);
        float currentSpeed = current.speedMps();
        float dirDeg = current.directionDeg();

        // Storm-based boost
        float chance = ForecastOrchestrator.getCurrentStormChance(key, serverLevel.getGameTime());
        if (chance > 0.15f) {
            // Scale boost with storm chance; cap to avoid absurd values
            float finalSpeed = getFinalSpeed(chance, currentSpeed, region);
            WindVector.set(key, finalSpeed, dirDeg);
        }
    }

    private static float getFinalSpeed(float chance, float currentSpeed, ScAPICloudRegion region) {
        float stormBoost = Math.min(12.0f, 3.0f + chance * 12.0f); // 3..15 m/s
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

    public static void onModifyCloudSpeed(ModifyCloudSpeedEvent event) {
        // Can adjust cloud speed if needed
    }
}

package net.Gabou.projectatmosphere.event;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.ScAPICloudRegion;
import dev.nonamecrackers2.simpleclouds.api.common.event.CloudRegionNaturallySpawnEvent;
import dev.nonamecrackers2.simpleclouds.api.common.event.CloudRegionRemovedEvent;
import dev.nonamecrackers2.simpleclouds.api.common.event.CloudRegionTickEvent;
import dev.nonamecrackers2.simpleclouds.api.common.event.ModifyCloudSpeedEvent;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.AtmosphereCloudRegionTracker;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.wind.WindEngine;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.util.ICloudRegionId;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SimpleCloudsEventListener {
    private static final float CLOUD_DIRECTION_VARIANCE_RAD = 0.15f;
    private static final float CLOUD_DIRECTION_WOBBLE_RAD = 0.06f;
    private static final float CLOUD_SPEED_VARIANCE = 0.12f;
    private static final int CLOUD_DRIFT_PERIOD_TICKS = 24000;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    @SubscribeEvent
    public static void onCloudRegionSpawn(CloudRegionNaturallySpawnEvent event) {
       if(!(event.getLevel() instanceof ServerLevel serverLevel)) {
           return;
        }
        CloudRegion region =(CloudRegion) event.getCloudRegion();
        AtmosphereCloudRegionTracker.queueAdd(region);
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

        RegionInstanceKey regionKey = RegionInstanceKey.from(new BlockPos(x, y, z));
        WindVector current = WindEngine.getCurrentHighWindVector(regionKey, serverLevel.getGameTime());
        float currentSpeed = current.baseSpeed();
        float dirDeg = (float) Math.toDegrees(current.angleRadians());

        // Storm-based boost
        float stormFactor = ForecastOrchestrator.getCurrentStormChance(regionKey, serverLevel.getGameTime());
        if (stormFactor > 0.15f) {
            // Scale boost with storm activity; cap to avoid absurd values
            float finalSpeed = getFinalSpeed(stormFactor, currentSpeed, region);
            WindVector.set(regionKey, finalSpeed, dirDeg);
        }

        applyCloudShear(event, current, serverLevel.getGameTime());
    }

    private static float getFinalSpeed(float stormFactor, float currentSpeed, ScAPICloudRegion region) {
        float stormBoost = Math.min(12.0f, 3.0f + stormFactor * 12.0f); // 3..15 m/s
        float boosted = Math.max(currentSpeed, stormBoost);

        // Extra amplification if a tornado is active in this cloud region vicinity
        float tornadoBoost = 0f;
        for (var t : TornadoManager.getActiveTornadoes()) {
            var cr = t.getCloudRegion();
            double centerX = cr != null ? cr.getWorldX() : t.position.x;
            double centerZ = cr != null ? cr.getWorldZ() : t.position.z;
            // Standalone tornadoes do not have a Simple Clouds region; compare against the tornado position instead.
            double dx = centerX - region.getWorldX();
            double dz = centerZ - region.getWorldZ();
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

    private static void applyCloudShear(CloudRegionTickEvent event, WindVector wind, long gameTime) {
        ScAPICloudRegion region = event.getCloudRegion();
        int id = getStableCloudId(region);
        float baseOffset = (hashToUnit(id) - 0.5f) * 2.0f * CLOUD_DIRECTION_VARIANCE_RAD;
        float wobblePhase = hashToUnit(id * 31 + 7) * TWO_PI;
        float wobble = Mth.sin((gameTime / (float) CLOUD_DRIFT_PERIOD_TICKS) * TWO_PI + wobblePhase) * CLOUD_DIRECTION_WOBBLE_RAD;
        float angle = wind.angleRadians() + baseOffset + wobble;
        Vec2 direction = new Vec2((float) -Math.sin(angle), (float) Math.cos(angle));
        if (direction.length() > 0.001f) {
            event.setModifiedMovementDirection(direction.normalized());
        }

        float speedScale = 1.0f + (hashToUnit(id * 13 + 101) - 0.5f) * 2.0f * CLOUD_SPEED_VARIANCE;
        float baseMaxSpeed = region.getMaxSpeed();
        float scaledSpeed = Math.max(0.001f, baseMaxSpeed * speedScale);
        event.setModifiedMaxSpeed(scaledSpeed);
    }

    private static int getStableCloudId(ScAPICloudRegion region) {
        if (region instanceof ICloudRegionId id) {
            return id.projectatmosphere$getId();
        }
        return System.identityHashCode(region);
    }

    private static float hashToUnit(int value) {
        int x = value;
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return (x & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }
}

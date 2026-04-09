package net.Gabou.projectatmosphere.modules.hurricane;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.SyncHurricaneStatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HurricaneManager {
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final int ATMOSPHERE_INTERVAL_TICKS = 20;
    private static final int LIGHTNING_INTERVAL_TICKS = 40;
    private static final List<HurricaneInstance> ACTIVE_HURRICANES = new ArrayList<>();
    private static final Map<java.util.UUID, CloudRegion> RESERVATION_REGIONS = new LinkedHashMap<>();
    private static boolean dirty = true;

    public static void spawnServer(ServerLevel level, Vec3 pos, float radius, WindVector wind, HurricaneCategory category) {
        HurricaneInstance hurricane = new HurricaneInstance(pos, radius, wind, category);
        ACTIVE_HURRICANES.add(hurricane);
        RESERVATION_REGIONS.put(hurricane.id, HurricaneSemantics.createReservationRegion(hurricane));
        dirty = true;
        syncToDimension(level);
    }

    public static void tick(ServerLevel level) {
        if (ACTIVE_HURRICANES.removeIf(h -> h.getLifetimeSeconds() > 1200.0F)) {
            dirty = true;
        }
        for (HurricaneInstance hurricane : ACTIVE_HURRICANES) {
            float speed = hurricane.wind.baseSpeed() * 0.01F;
            hurricane.position = hurricane.position.add(
                    Math.cos(hurricane.wind.angleRadians()) * speed,
                    0,
                    Math.sin(hurricane.wind.angleRadians()) * speed);
            hurricane.tick(level);
        }
        projectatmosphere$syncReservationRegions();
        if (!ACTIVE_HURRICANES.isEmpty() && level.getGameTime() % ATMOSPHERE_INTERVAL_TICKS == 0L) {
            applyAtmosphereForcing(level);
            reconcileReservedCloudSpace(level);
        }
        if (!ACTIVE_HURRICANES.isEmpty() && level.getGameTime() % LIGHTNING_INTERVAL_TICKS == 0L) {
            spawnEyewallLightning(level);
        }
        if (dirty || level.getGameTime() % SYNC_INTERVAL_TICKS == 0L) {
            syncToDimension(level);
            dirty = false;
        }
    }

    public static List<HurricaneInstance> getActiveHurricanes() {
        return Collections.unmodifiableList(ACTIVE_HURRICANES);
    }

    public static void clearHurricanes() {
        ACTIVE_HURRICANES.clear();
        RESERVATION_REGIONS.clear();
        dirty = true;
    }

    public static void removeHurricane(HurricaneInstance hurricane) {
        if (ACTIVE_HURRICANES.remove(hurricane)) {
            RESERVATION_REGIONS.remove(hurricane.id);
            dirty = true;
        }
    }

    public static CloudRegion getReservationRegionAt(double worldX, double worldZ) {
        for (HurricaneInstance hurricane : ACTIVE_HURRICANES) {
            if (HurricaneSemantics.intersectsReservation(hurricane, worldX, worldZ, 0.0D)) {
                CloudRegion region = RESERVATION_REGIONS.get(hurricane.id);
                if (region != null) {
                    return region;
                }
            }
        }
        return null;
    }

    public static void syncToPlayer(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), createSyncPacket());
    }

    private static void syncToDimension(ServerLevel level) {
        NetworkHandler.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), createSyncPacket());
    }

    private static SyncHurricaneStatePacket createSyncPacket() {
        return new SyncHurricaneStatePacket(ACTIVE_HURRICANES.stream()
                .map(HurricaneInstance::createRenderSnapshot)
                .collect(Collectors.toList()));
    }

    private static void applyAtmosphereForcing(ServerLevel level) {
        List<RegionAtmosphereState> snapshot = AtmosphericStateRegistry.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        for (RegionAtmosphereState state : snapshot) {
            float cloudCeil = 0.0F;
            float rainCeil = 0.0F;
            float cloudWater = 0.0F;
            float humidityGain = 0.0F;
            float pressureDrop = 0.0F;
            float cooling = 0.0F;
            for (HurricaneInstance hurricane : ACTIVE_HURRICANES) {
                float forcingRadius = hurricane.getStormExtentRadius() * 0.96F;
                double distance = state.distanceTo(hurricane.position.x, hurricane.position.z);
                if (distance > forcingRadius) {
                    continue;
                }
                float influence = 1.0F - (float) (distance / forcingRadius);
                influence = Mth.clamp(influence, 0.0F, 1.0F);
                float eyeRadius = hurricane.getVisualEyeRadius();
                float coreRadius = hurricane.getCoreRadius();
                boolean eyewallZone = distance >= eyeRadius * 1.3F && distance <= coreRadius * 0.9F;
                float stormWeight = influence * (eyewallZone ? 1.0F : 0.82F);
                cloudCeil = Math.max(cloudCeil, Mth.clamp(0.78F + stormWeight * 0.22F, 0.0F, 1.0F));
                rainCeil = Math.max(rainCeil, Mth.clamp(0.52F + stormWeight * 0.48F, 0.0F, 1.0F));
                cloudWater = Math.max(cloudWater, Mth.clamp(0.40F + stormWeight * 0.80F, 0.0F, 1.2F));
                humidityGain = Math.max(humidityGain, stormWeight * 0.16F);
                pressureDrop = Math.max(pressureDrop, stormWeight * 11.5F);
                cooling = Math.max(cooling, stormWeight * 4.5F);
            }
            if (cloudCeil <= 0.0F && rainCeil <= 0.0F) {
                continue;
            }
            state.adjustHumidity(humidityGain);
            state.adjustPressure(-pressureDrop);
            state.adjustTemperature(-cooling);
            state.applyCycloneVisualFloor(cloudCeil, rainCeil);
            state.setCloudCover(Math.max(state.getCloudCover(), cloudCeil));
            state.setRainIntensity(Math.max(state.getRainIntensity(), rainCeil));
            state.setCloudWater(Math.max(state.getCloudWater(), cloudWater));
        }
    }

    private static void reconcileReservedCloudSpace(ServerLevel level) {
        if (ACTIVE_HURRICANES.isEmpty()) {
            return;
        }
        CloudManager<?> cloudManager = CloudManager.get(level);
        if (cloudManager == null) {
            return;
        }
        CloudGenerator generator = cloudManager.getCloudGenerator();
        double padding = SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS;
        generator.removeClouds(region -> ACTIVE_HURRICANES.stream().anyMatch(hurricane ->
                HurricaneSemantics.intersectsReservation(hurricane, region.getWorldX(), region.getWorldZ(), region.getWorldRadius() + padding)
        ));
    }

    private static void spawnEyewallLightning(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }
        for (HurricaneInstance hurricane : ACTIVE_HURRICANES) {
            float activityRadius = hurricane.getStormExtentRadius() + 512.0F;
            if (!projectatmosphere$isPlayerNear(level, hurricane, activityRadius)) {
                continue;
            }
            float strikeChance = 0.18F + hurricane.category.ordinal() * 0.08F;
            if (level.random.nextFloat() > strikeChance) {
                continue;
            }

            float minRadius = hurricane.getVisualEyeRadius() * 1.3F;
            float maxRadius = hurricane.getCoreRadius() * 0.9F;
            if (maxRadius <= minRadius) {
                maxRadius = minRadius + 16.0F;
            }

            float angle = level.random.nextFloat() * Mth.TWO_PI;
            float radius = Mth.lerp(level.random.nextFloat(), minRadius, maxRadius);
            double strikeX = hurricane.position.x + Math.cos(angle) * radius;
            double strikeZ = hurricane.position.z + Math.sin(angle) * radius;
            int blockX = Mth.floor(strikeX);
            int blockZ = Mth.floor(strikeZ);
            int blockY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);

            LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(level);
            if (lightningBolt == null) {
                continue;
            }
            lightningBolt.moveTo(strikeX, blockY, strikeZ);
            lightningBolt.setVisualOnly(false);
            level.addFreshEntity(lightningBolt);
        }
    }

    private static void projectatmosphere$syncReservationRegions() {
        RESERVATION_REGIONS.entrySet().removeIf(entry ->
                ACTIVE_HURRICANES.stream().noneMatch(hurricane -> hurricane.id.equals(entry.getKey()))
        );
        for (HurricaneInstance hurricane : ACTIVE_HURRICANES) {
            CloudRegion region = RESERVATION_REGIONS.computeIfAbsent(hurricane.id, id -> HurricaneSemantics.createReservationRegion(hurricane));
            HurricaneSemantics.updateReservationRegion(region, hurricane);
        }
    }

    private static boolean projectatmosphere$isPlayerNear(ServerLevel level, HurricaneInstance hurricane, float radius) {
        float radiusSq = radius * radius;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - hurricane.position.x;
            double dz = player.getZ() - hurricane.position.z;
            if (dx * dx + dz * dz <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}

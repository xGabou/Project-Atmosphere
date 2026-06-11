package net.Gabou.projectatmosphere.modules.hurricane;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneManager;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneSnapshot;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.SyncHurricaneStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class HurricaneManager {
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final int ATMOSPHERE_INTERVAL_TICKS = 20;
    private static final int LIGHTNING_INTERVAL_TICKS = 40;
    private static final int INTENSIFICATION_REQUIRED_TICKS = 20 * 30;
    private static final int DISSIPATION_GRACE_TICKS = 20 * 90;

    private static final Map<UUID, HurricaneInstance> LINKED_HURRICANES = new LinkedHashMap<>();
    private static final List<HurricaneInstance> DEBUG_HURRICANES = new ArrayList<>();
    private static final Map<UUID, FormationTracker> FORMATION_TRACKERS = new LinkedHashMap<>();
    private static final Map<UUID, HurricaneEnvironmentAnalyzer.CycloneEnvironment> ENVIRONMENT_CACHE = new LinkedHashMap<>();
    private static final Map<UUID, CloudRegion> RESERVATION_REGIONS = new LinkedHashMap<>();
    private static boolean dirty = true;

    private HurricaneManager() {
    }

    public static void spawnServer(ServerLevel level, Vec3 pos, float radius, WindVector wind, HurricaneCategory category) {
        HurricaneInstance hurricane = HurricaneInstance.createDebug(pos, radius, wind, category);
        hurricane.refreshAnchorY(level);
        DEBUG_HURRICANES.add(hurricane);
        projectatmosphere$syncManagedRegion(level, hurricane);
        dirty = true;
        syncToDimension(level);
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();

        projectatmosphere$tickDebugHurricanes(level);
        projectatmosphere$syncCycloneHurricanes(level, gameTime);
        projectatmosphere$syncReservationRegions(level);

        List<HurricaneInstance> active = projectatmosphere$getAllHurricanes();
        if (!active.isEmpty() && gameTime % ATMOSPHERE_INTERVAL_TICKS == 0L) {
            applyAtmosphereAmplification(level, active);
            reconcileReservedCloudSpace(level, active);
        }
        if (!active.isEmpty() && gameTime % LIGHTNING_INTERVAL_TICKS == 0L) {
            spawnEyewallLightning(level, active);
        }
        if (dirty || gameTime % SYNC_INTERVAL_TICKS == 0L) {
            syncToDimension(level);
            dirty = false;
        }
    }

    public static List<HurricaneInstance> getActiveHurricanes() {
        return List.copyOf(projectatmosphere$getAllHurricanes());
    }

    public static List<HurricaneInstance> getClientHurricanes() {
        return getActiveHurricanes();
    }

    public static void clearHurricanes() {
        resetRuntimeState(null);
    }

    public static void clearHurricanes(ServerLevel level) {
        resetRuntimeState(level);
    }

    public static List<CompoundTag> savePersistentHurricanes() {
        List<CompoundTag> tags = new ArrayList<>();
        for (HurricaneInstance hurricane : projectatmosphere$getAllHurricanes()) {
            if (!hurricane.isDead()) {
                tags.add(hurricane.toPersistentTag());
            }
        }
        return tags;
    }

    public static void loadPersistentHurricanes(ServerLevel level, List<CompoundTag> tags) {
        resetRuntimeState(level);

        for (CompoundTag tag : tags) {
            HurricaneInstance hurricane = HurricaneInstance.fromPersistentTag(tag);
            hurricane.refreshAnchorY(level);
            registerLoadedHurricane(level, hurricane);
        }
        dirty = true;
    }

    public static void removeHurricane(HurricaneInstance hurricane) {
        if (hurricane == null) {
            return;
        }

        if ((DEBUG_HURRICANES.contains(hurricane) || hurricane.isLinkedToCyclone())
                && hurricane.getPhase() != StormLifecyclePhase.DISSIPATING
                && hurricane.getPhase() != StormLifecyclePhase.DISSIPATED) {
            hurricane.markDissipating();
            dirty = true;
        }
    }

    public static CloudRegion getReservationRegionAt(double worldX, double worldZ) {
        for (HurricaneInstance hurricane : projectatmosphere$getAllHurricanes()) {
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

    private static void resetRuntimeState() {
        resetRuntimeState(null);
    }

    private static void resetRuntimeState(ServerLevel level) {
        projectatmosphere$removeManagedRegions(level);
        LINKED_HURRICANES.clear();
        DEBUG_HURRICANES.clear();
        FORMATION_TRACKERS.clear();
        ENVIRONMENT_CACHE.clear();
        RESERVATION_REGIONS.clear();
        dirty = true;
    }

    private static void registerLoadedHurricane(ServerLevel level, HurricaneInstance hurricane) {
        if (hurricane.isDebugSpawn() || hurricane.getCycloneId() == null) {
            DEBUG_HURRICANES.add(hurricane);
        } else {
            LINKED_HURRICANES.put(hurricane.getCycloneId(), hurricane);
        }
        projectatmosphere$syncManagedRegion(level, hurricane);
    }

    private static SyncHurricaneStatePacket createSyncPacket() {
        return new SyncHurricaneStatePacket(projectatmosphere$getAllHurricanes().stream()
                .map(HurricaneInstance::createRenderSnapshot)
                .collect(Collectors.toList()));
    }

    private static void projectatmosphere$tickDebugHurricanes(ServerLevel level) {
        List<HurricaneInstance> dead = new ArrayList<>();
        for (HurricaneInstance hurricane : DEBUG_HURRICANES) {
            float speed = hurricane.wind.baseSpeed() * 0.01F;
            hurricane.position = hurricane.position.add(
                    Math.cos(hurricane.wind.angleRadians()) * speed,
                    0.0D,
                    Math.sin(hurricane.wind.angleRadians()) * speed
            );
            hurricane.tick(level);
            if (hurricane.isDead() || hurricane.getLifetimeSeconds() > 1200.0F) {
                dead.add(hurricane);
            }
        }

        if (!dead.isEmpty()) {
            for (HurricaneInstance hurricane : dead) {
                DEBUG_HURRICANES.remove(hurricane);
                projectatmosphere$removeManagedRegion(level, hurricane.id);
            }
            dirty = true;
        }
    }

    private static void projectatmosphere$syncCycloneHurricanes(ServerLevel level, long gameTime) {
        List<CycloneSnapshot> snapshots = CycloneManager.getActiveCycloneSnapshots();
        Set<UUID> activeCyclones = snapshots.stream().map(CycloneSnapshot::id).collect(Collectors.toCollection(LinkedHashSet::new));

        boolean anyDissipating = false;
        for (Map.Entry<UUID, HurricaneInstance> entry : LINKED_HURRICANES.entrySet()) {
            if (activeCyclones.contains(entry.getKey())) {
                continue;
            }
            HurricaneInstance hurricane = entry.getValue();
            if (hurricane.getPhase() != StormLifecyclePhase.DISSIPATING
                    && hurricane.getPhase() != StormLifecyclePhase.DISSIPATED) {
                hurricane.markDissipating();
                anyDissipating = true;
            }
        }
        if (anyDissipating) {
            dirty = true;
        }
        FORMATION_TRACKERS.keySet().removeIf(id -> !activeCyclones.contains(id));
        ENVIRONMENT_CACHE.keySet().removeIf(id -> !activeCyclones.contains(id));

        boolean refreshEnvironment = gameTime % ATMOSPHERE_INTERVAL_TICKS == 0L;
        for (CycloneSnapshot snapshot : snapshots) {
            HurricaneEnvironmentAnalyzer.CycloneEnvironment environment = ENVIRONMENT_CACHE.get(snapshot.id());
            if (environment == null || refreshEnvironment) {
                environment = HurricaneEnvironmentAnalyzer.analyzeCyclone(level, snapshot);
                ENVIRONMENT_CACHE.put(snapshot.id(), environment);
            }

            FormationTracker tracker = FORMATION_TRACKERS.computeIfAbsent(snapshot.id(), ignored -> new FormationTracker());
            tracker.update(environment.formationEligible(snapshot), environment.sustainEligible(snapshot));

            HurricaneInstance hurricane = LINKED_HURRICANES.get(snapshot.id());
            if (hurricane == null) {
                if (tracker.readyToIntensify()) {
                    HurricaneInstance created = HurricaneInstance.fromCyclone(
                            level,
                            snapshot,
                            environment.wind(),
                            environment.targetCategory(snapshot),
                            environment.intensificationStrength()
                    );
                    LINKED_HURRICANES.put(snapshot.id(), created);
                    projectatmosphere$syncManagedRegion(level, created);
                    dirty = true;
                }
                continue;
            }

            hurricane.updateFromCyclone(
                    level,
                    snapshot,
                    environment.wind(),
                    environment.targetCategory(snapshot),
                    environment.intensificationStrength()
            );
            hurricane.tick(level);

            if (tracker.shouldDissipate(snapshot)) {
                if (hurricane.getPhase() != StormLifecyclePhase.DISSIPATING
                        && hurricane.getPhase() != StormLifecyclePhase.DISSIPATED) {
                    hurricane.markDissipating();
                    dirty = true;
                }
            }

            if (hurricane.isDead()) {
                LINKED_HURRICANES.remove(snapshot.id());
                projectatmosphere$removeManagedRegion(level, hurricane.id);
                dirty = true;
            }
        }

        Iterator<Map.Entry<UUID, HurricaneInstance>> iterator = LINKED_HURRICANES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, HurricaneInstance> entry = iterator.next();
            if (activeCyclones.contains(entry.getKey())) {
                continue;
            }

            HurricaneInstance hurricane = entry.getValue();
            hurricane.tick(level);
            if (hurricane.isDead()) {
                projectatmosphere$removeManagedRegion(level, hurricane.id);
                iterator.remove();
                dirty = true;
            }
        }
    }

    private static void applyAtmosphereAmplification(ServerLevel level, List<HurricaneInstance> hurricanes) {
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

            for (HurricaneInstance hurricane : hurricanes) {
                float forcingRadius = hurricane.getStormExtentRadius();
                double distance = state.distanceTo(hurricane.position.x, hurricane.position.z);
                if (distance > forcingRadius) {
                    continue;
                }

                float influence = 1.0F - (float) (distance / forcingRadius);
                influence = Mth.clamp(influence, 0.0F, 1.0F);
                float eyeRadius = hurricane.getVisualEyeRadius();
                float coreRadius = hurricane.getCoreRadius();
                boolean eyewallZone = distance >= eyeRadius * 1.3F && distance <= coreRadius * 0.9F;
                float stormWeight = influence * (eyewallZone ? 1.0F : 0.78F);
                cloudCeil = Math.max(cloudCeil, Mth.clamp(0.72F + stormWeight * 0.28F, 0.0F, 1.0F));
                rainCeil = Math.max(rainCeil, Mth.clamp(0.45F + stormWeight * 0.55F, 0.0F, 1.0F));
                cloudWater = Math.max(cloudWater, Mth.clamp(0.35F + stormWeight * 0.80F, 0.0F, 1.2F));
                humidityGain = Math.max(humidityGain, stormWeight * 0.08F);
                pressureDrop = Math.max(pressureDrop, stormWeight * 3.5F);
                cooling = Math.max(cooling, stormWeight * 1.6F);
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

    private static void reconcileReservedCloudSpace(ServerLevel level, List<HurricaneInstance> hurricanes) {
        CloudManager<?> cloudManager = CloudManager.get(level);
        if (cloudManager == null) {
            return;
        }

        CloudGenerator generator = cloudManager.getCloudGenerator();
        double padding = SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS;
        generator.removeClouds(region -> hurricanes.stream().anyMatch(hurricane ->
                HurricaneSemantics.intersectsReservation(hurricane, region.getWorldX(), region.getWorldZ(), region.getWorldRadius() + padding)
        ));
    }

    private static void spawnEyewallLightning(ServerLevel level, List<HurricaneInstance> hurricanes) {
        if (level.players().isEmpty()) {
            return;
        }

        for (HurricaneInstance hurricane : hurricanes) {
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

    private static void projectatmosphere$syncReservationRegions(ServerLevel level) {
        List<HurricaneInstance> hurricanes = projectatmosphere$getAllHurricanes();
        Set<UUID> activeIds = hurricanes.stream().map(hurricane -> hurricane.id).collect(Collectors.toSet());
        for (Map.Entry<UUID, CloudRegion> entry : new ArrayList<>(RESERVATION_REGIONS.entrySet())) {
            if (activeIds.contains(entry.getKey())) {
                continue;
            }
            projectatmosphere$removeManagedRegion(level, entry.getKey());
        }

        for (HurricaneInstance hurricane : hurricanes) {
            projectatmosphere$syncManagedRegion(level, hurricane);
        }
    }

    private static void projectatmosphere$syncManagedRegion(ServerLevel level, HurricaneInstance hurricane) {
        CloudRegion region = RESERVATION_REGIONS.get(hurricane.id);
        if (region == null) {
            region = HurricaneSemantics.createReservationRegion(hurricane);
            if (region == null) {
                return;
            }
            RESERVATION_REGIONS.put(hurricane.id, region);
            projectatmosphere$addManagedRegion(level, hurricane, region);
        }
        HurricaneSemantics.updateReservationRegion(region, hurricane);
        projectatmosphere$addManagedRegion(level, hurricane, region);
    }

    private static void projectatmosphere$addManagedRegion(ServerLevel level, HurricaneInstance hurricane, CloudRegion region) {
        if (level == null || region == null || !AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return;
        }
        CloudManager<?> cloudManager = CloudManager.get(level);
        if (cloudManager == null) {
            return;
        }
        CloudGenerator generator = cloudManager.getCloudGenerator();
        if (generator.addCloud(region, CloudGenerator.Order.USE_WEIGHT)) {
            return;
        }
        if (generator.getClouds().contains(region)) {
            return;
        }

        double padding = SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS;
        generator.removeClouds(candidate ->
                HurricaneSemantics.intersectsReservation(hurricane, candidate.getWorldX(), candidate.getWorldZ(), candidate.getWorldRadius() + padding)
        );
        generator.addCloud(region, CloudGenerator.Order.USE_WEIGHT);
    }

    private static void projectatmosphere$removeManagedRegion(ServerLevel level, UUID hurricaneId) {
        CloudRegion region = RESERVATION_REGIONS.remove(hurricaneId);
        if (region == null) {
            return;
        }
        if (level == null || !AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return;
        }
        CloudManager<?> cloudManager = CloudManager.get(level);
        if (cloudManager == null) {
            return;
        }
        cloudManager.getCloudGenerator().removeClouds(candidate -> candidate == region);
    }

    private static void projectatmosphere$removeManagedRegions(ServerLevel level) {
        if (RESERVATION_REGIONS.isEmpty()) {
            return;
        }

        if (level != null && AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            CloudManager<?> cloudManager = CloudManager.get(level);
            if (cloudManager != null) {
                for (CloudRegion region : new ArrayList<>(RESERVATION_REGIONS.values())) {
                    if (region != null) {
                        cloudManager.getCloudGenerator().removeClouds(candidate -> candidate == region);
                    }
                }
            }
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

    private static List<HurricaneInstance> projectatmosphere$getAllHurricanes() {
        if (LINKED_HURRICANES.isEmpty()) {
            return new ArrayList<>(DEBUG_HURRICANES);
        }
        List<HurricaneInstance> hurricanes = new ArrayList<>(LINKED_HURRICANES.size() + DEBUG_HURRICANES.size());
        hurricanes.addAll(LINKED_HURRICANES.values());
        hurricanes.addAll(DEBUG_HURRICANES);
        return hurricanes;
    }

    private static final class FormationTracker {
        private int qualifyingTicks;
        private int weakTicks;

        private void update(boolean formationEligible, boolean sustainEligible) {
            if (formationEligible) {
                this.qualifyingTicks = Math.min(INTENSIFICATION_REQUIRED_TICKS, this.qualifyingTicks + ATMOSPHERE_INTERVAL_TICKS);
                this.weakTicks = 0;
                return;
            }

            this.qualifyingTicks = Math.max(0, this.qualifyingTicks - ATMOSPHERE_INTERVAL_TICKS);
            if (sustainEligible) {
                this.weakTicks = Math.max(0, this.weakTicks - ATMOSPHERE_INTERVAL_TICKS);
            } else {
                this.weakTicks += ATMOSPHERE_INTERVAL_TICKS;
            }
        }

        private boolean readyToIntensify() {
            return this.qualifyingTicks >= INTENSIFICATION_REQUIRED_TICKS;
        }

        private boolean shouldDissipate(CycloneSnapshot snapshot) {
            return snapshot.intensity() < 0.38F || this.weakTicks >= DISSIPATION_GRACE_TICKS;
        }
    }

}

package net.Gabou.projectatmosphere.compat;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.spawning.SpawnInfo;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsRollbackDebugger;
import net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsTrackingIdentity;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.tools.debug.SimpleCloudsRenderDiagnostics;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.modules.weather.WeatherSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector2i;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.Gabou.projectatmosphere.manager.CloudSpawnSeverityRules.calculateDewPoint;
import static net.Gabou.projectatmosphere.manager.CloudSpawnSeverityRules.determineCloudSeverity;

public class SimpleCloudsCompat {

    public static ServerCloudManager cloudManager;
    public static CloudGenerator generator;

    public static RandomSource random = RandomSource.create();
    public static CloudSpawningConfig spawnConfig;

    public static final int SCALE = SimpleCloudsConstants.CLOUD_SCALE;

    public static final int MIN_RADIUS = Math.round(5000F/ SCALE);
    private static final float ACCEL_PER_WIND = 0.0005F;

    private static final float MIN_PER_TICK = 0.001F; // very gentle drift
    private static final float MAX_PER_TICK = 0.02F;  // about one block per second

    public static final int MAX_RADIUS = Math.round(9429F / SCALE);

    // ---------------------------------------------------------------------
    // Initialization
    // ---------------------------------------------------------------------
    public static void configureConstants() {
        SimpleCloudsConstants.SPAWN_RADIUS = Math.min(ProjectAtmosphere.DEFAULT_RADIUS / 5, 10000);
    }

    public static void init(ServerLevel level) {
        cloudManager = (ServerCloudManager) CloudManager.get(level);
        if (cloudManager == null) {
            generator = null;
            spawnConfig = null;
            isInit = false;
            return;
        }

        generator = cloudManager.getCloudGenerator();
        if (generator == null) {
            spawnConfig = null;
            isInit = false;
            return;
        }

        spawnConfig = generator.getSpawnConfig().get();
        isInit = spawnConfig != null;
    }

    public static boolean ensureReady(ServerLevel level) {
        if (level == null) {
            return false;
        }
        if (cloudManager != null && generator != null && spawnConfig != null) {
            isInit = true;
            return true;
        }

        init(level);
        if (generator != null && spawnConfig != null) {
            return true;
        }

        if (cloudManager == null) {
            cloudManager = (ServerCloudManager) CloudManager.get(level);
        }
        if (cloudManager == null) {
            return false;
        }

        if (generator == null) {
            generator = cloudManager.getCloudGenerator();
        }
        if (generator == null) {
            isInit = false;
            return false;
        }

        if (spawnConfig == null && generator.getSpawnConfig() != null) {
            spawnConfig = generator.getSpawnConfig().get();
        }
        isInit = generator != null && spawnConfig != null;
        return isInit;
    }

    private static boolean isInit = false;

    public static void setIsInit(boolean b) {
        isInit = b;
    }
    public static boolean getIsInit() {
        return isInit;
    }

    // ---------------------------------------------------------------------
    // Spawn entry points
    // ---------------------------------------------------------------------

    public static CloudRegion spawnCloudInRegion(String cloudId, RegionInstanceKey key, ServerLevel level, @Nullable CloudRegion dummy, WindVector windVector) {
        if (key == null) {
            return null;
        }
        return spawnCloud(cloudId, key.center(), null, level, dummy, windVector);
    }

    public static CloudRegion spawnCloudAt(String cloudId, BlockPos anchor, ServerLevel level, @Nullable CloudRegion dummy, WindVector windVector) {
        if (anchor == null) {
            return null;
        }
        return spawnCloud(cloudId, anchor, null, level, dummy, windVector);
    }

    public static CloudRegion ensureCloudAtPosition(BlockPos pos, ServerLevel level) {
        if (pos == null || level == null || generator == null || spawnConfig == null) {
            return null;
        }
        if (generator.getCloudAtWorldPosition(pos.getX(), pos.getZ()) != null) {
            return null;
        }

        Map<RegionInstanceKey, Integer> sampledRegions = WeatherSampler.sampleRegionsInArea(
                pos.getX(),
                pos.getZ(),
                (int) ProjectAtmosphere.DEFAULT_REGION_RADIUS,
                level
        );
        WeatherSampler.WeatherStats stats = WeatherSampler.computeWeatherStats(sampledRegions, level.getGameTime());
        RegionInstanceKey currentRegion = RegionInstanceKey.from(pos);
        WindVector wind = stats == null ? WindVector.fromBase(1f, 0f) : stats.windVector();
        int severity = stats == null ? 1 : determineCloudSeverity(
                stats.temperature(),
                stats.humidity(),
                stats.pressure(),
                calculateDewPoint(stats.temperature(), stats.humidity()),
                stats.stormFactor(),
                level
        );
        String cloudId = CloudLibrary.getCloudIdFromSeverity(Math.max(1, severity));
        CloudSpawningConfig.Info info = resolveSpawnInfo(cloudId);
        if (info == null) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Could not find a SimpleClouds spawn info for forced local cloud at {}", currentRegion);
            return null;
        }

        SpawnRegion activeRegion = generator.getSpawnRegions().stream()
                .filter(r -> r.includesPoint(pos.getX(), pos.getZ()))
                .findFirst()
                .orElseGet(() -> new SpawnRegion(pos.getX(), pos.getZ(), SimpleCloudsConstants.SPAWN_RADIUS));

        Optional<CloudRegion> candidate = createLocalCloudCandidate(info, pos, level, wind);
        if (candidate.isEmpty()) {
            CloudRegion nearest = nearestNonCoveringCloud(activeRegion, pos);
            if (nearest == null) {
                return null;
            }
            SimpleCloudsTrackingIdentity.Entry removedIdentity = SimpleCloudsTrackingIdentity.resolve(nearest, level);
            SimpleCloudsRollbackDebugger.markSimpleCloudWrite("ensure_coverage_remove_nearest", removedIdentity.trackingKey(), level);
            generator.removeClouds(existing -> existing == nearest);
            candidate = createLocalCloudCandidate(info, pos, level, wind);
            if (candidate.isEmpty()) {
                return null;
            }
        }

        CloudRegion cloud = candidate.get();
        if (generator.addCloud(cloud, CloudGenerator.Order.USE_WEIGHT)) {
            SimpleCloudsTrackingIdentity.Entry identity = SimpleCloudsTrackingIdentity.resolve(cloud, level);
            SimpleCloudsRollbackDebugger.markSimpleCloudWrite("ensure_coverage_add", identity.trackingKey(), level);
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Forced local cloud coverage for {} at {}", currentRegion, pos);
            return cloud;
        }

        CloudRegion farthest = generator.getCloudsInRegion(activeRegion).stream()
                .filter(existing -> generator.getCloudAtWorldPosition(pos.getX(), pos.getZ()) != existing)
                .max(Comparator.comparingDouble(existing -> distanceSquared(existing, pos)))
                .orElse(null);
        if (farthest != null) {
            SimpleCloudsTrackingIdentity.Entry removedIdentity = SimpleCloudsTrackingIdentity.resolve(farthest, level);
            SimpleCloudsRollbackDebugger.markSimpleCloudWrite("ensure_coverage_replace_remove", removedIdentity.trackingKey(), level);
            generator.removeClouds(existing -> existing == farthest);
            if (generator.addCloud(cloud, CloudGenerator.Order.USE_WEIGHT)) {
                SimpleCloudsTrackingIdentity.Entry identity = SimpleCloudsTrackingIdentity.resolve(cloud, level);
                SimpleCloudsRollbackDebugger.markSimpleCloudWrite("ensure_coverage_replace_add", identity.trackingKey(), level);
                ProjectAtmosphere.LOGGER.info("[Atmosphere] Replaced distant cloud to force local coverage for {} at {}", currentRegion, pos);
                return cloud;
            }
        }

        return null;
    }

    private static Optional<CloudRegion> createLocalCloudCandidate(CloudSpawningConfig.Info info, BlockPos pos, ServerLevel level, WindVector wind) {
        Optional<CloudRegion> candidate = createRegion(info, pos, level, random, wind, generator);
        candidate.ifPresent(cloud -> cloud.setWorldRadius(ProjectAtmosphere.DEFAULT_REGION_RADIUS));
        return candidate;
    }

    private static CloudRegion nearestNonCoveringCloud(SpawnRegion activeRegion, BlockPos pos) {
        return generator.getCloudsInRegion(activeRegion).stream()
                .filter(existing -> generator.getCloudAtWorldPosition(pos.getX(), pos.getZ()) != existing)
                .min(Comparator.comparingDouble(existing -> distanceSquared(existing, pos)))
                .orElse(null);
    }

    private static CloudSpawningConfig.Info resolveSpawnInfo(String preferredCloudId) {
        CloudSpawningConfig.Info info = spawnInfo(preferredCloudId);
        if (info != null) {
            return info;
        }
        for (String fallback : List.of("real_itty_bitty", "itty_bitty", "itty_bitty_bigger", "dense_itty_bitty")) {
            info = spawnInfo(fallback);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    private static CloudSpawningConfig.Info spawnInfo(String cloudId) {
        if (cloudId == null || spawnConfig == null) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID, cloudId);
        return spawnConfig.getWeightInfo(rl);
    }

    private static double distanceSquared(CloudRegion region, BlockPos pos) {
        double dx = region.getWorldX() - pos.getX();
        double dz = region.getWorldZ() - pos.getZ();
        return dx * dx + dz * dz;
    }

    // ---------------------------------------------------------------------
    // Region creation
    // ---------------------------------------------------------------------
    private static CloudRegion spawnCloud(String cloudId, BlockPos anchor, @Nullable ResourceLocation biomeId, ServerLevel level, @Nullable CloudRegion dummy, WindVector windVector) {

        if (!ensureReady(level)) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] SimpleClouds is not ready yet, cannot spawn cloud: {}", cloudId);
            return null;
        }


        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID, cloudId);
        CloudSpawningConfig.Info info = spawnConfig.getWeightInfo(rl);
        if (info == null) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Unknown cloud type: {}", cloudId);
            return null;
        }
        if(ProjectAtmosphere.DEBUG_MODE)
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Spawning cloud: " + cloudId);
        List<SpawnRegion> Region = generator.getSpawnRegions();
        SpawnRegion targetRegion = Region.iterator().next();


        float x = targetRegion.x() + 0.5F;
        float z = targetRegion.z() + 0.5F;
        Optional<CloudRegion> region;
        if (dummy != null) {
            region = generator.spawnCloud(() -> info, spawnConfig.getSpawnInterval().sample(random), spawnConfig.getMaxRegions(), level,
                    (spawnInfo, playerX, playerZ, realX, realZ, rand, grow) ->
                            regionDummy(dummy)
            );
        } else {
            region = generator.spawnCloud(() -> info, spawnConfig.getSpawnInterval().sample(random), spawnConfig.getMaxRegions(), level,
                    (spawnInfo, playerX, playerZ, realX, realZ, rand, grow) ->
                            createRegion(spawnInfo, anchor, level, rand, windVector, generator)
            );
        }

        if(ProjectAtmosphere.DEBUG_MODE)
            region.ifPresentOrElse(
                r -> ProjectAtmosphere.LOGGER.info("[Atmosphere] Spawned {} at {}, {} near {}", cloudId, x, z, biomeId == null ? anchor : biomeId),
                () -> ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to spawn {} near {}", cloudId, biomeId == null ? anchor : biomeId)
            );
        region.ifPresent(cloud -> {
            SimpleCloudsTrackingIdentity.Entry identity = SimpleCloudsTrackingIdentity.resolve(cloud, level);
            SimpleCloudsRollbackDebugger.markSimpleCloudWrite("spawn_cloud", identity.trackingKey(), level);
        });
        return  region.orElse(null);
    }

    public static Optional<CloudRegion> regionDummy(CloudRegion region) {
        return Optional.of(region);
    }

    // ---------------------------------------------------------------------
    // Cloud region factory
    // ---------------------------------------------------------------------

    public static Optional<CloudRegion> createRegion(
            SpawnInfo info,
            RegionInstanceKey regionKey,
            ServerLevel level,
            RandomSource random,
            WindVector wind,
            CloudGenerator generator
    ) {
        if (regionKey == null) {
            return Optional.empty();
        }
        return createRegion(info, regionKey.center(), level, random, wind, generator);
    }

    public static Optional<CloudRegion> createRegion(
            SpawnInfo info,
            BlockPos anchor,
            ServerLevel level,
            RandomSource random,
            WindVector wind,
            CloudGenerator generator
    ) {
        float x = anchor.getX();
        float z = anchor.getZ();
        // Hard cap: skip spawn if candidate is farther than 10k from all players
        boolean nearPlayer = level.players().stream().anyMatch(p -> {
            double dx = x - p.getX();
            double dz = z - p.getZ();
            return (dx * dx + dz * dz) <= 10000d * 10000d;
        });
        if (!nearPlayer) {
            return Optional.empty();
        }
        float windAngleRad = wind.angleRadians();
        float dx = (float) -Math.sin(windAngleRad);
        float dz = (float) Math.cos(windAngleRad);
        Vec2 direction = new Vec2(dx, dz).normalized();
        float rotation = windAngleRad + (float) Math.PI;
        Optional<CloudRegion> region = generator.createRegion(info, 10, 10, x, z, random, true);
        if (region.isEmpty()) {
            return Optional.empty();
        }
        CloudRegion cloudRegion = region.get();
        cloudRegion.setMovementDirection(direction);
        cloudRegion.setRotation(rotation);
        float targetPerTick = wind.baseSpeed() / 20.0F; // if baseSpeed is blocks per second
        targetPerTick = Mth.clamp(targetPerTick, MIN_PER_TICK, MAX_PER_TICK);
        cloudRegion.setMaxSpeed(targetPerTick);
        float acc = cloudRegion.getAccelerationFactor();
        float accel = acc + ACCEL_PER_WIND * wind.baseSpeed();
        accel = Mth.clamp(accel, 0.001F, 0.01F);
        cloudRegion.setAccelerationFactor(accel);
        cloudRegion.setWorldRadius(ProjectAtmosphere.DEFAULT_REGION_RADIUS);

        return Optional.of(cloudRegion);
    }

    public static void doInitialGenWithWeather(int x, int z, ServerLevel level) {
        if (!ensureReady(level)) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Skipping Simple Clouds initial generation because the generator is not ready yet.");
            return;
        }

        List<SpawnRegion> regions = generator.getSpawnRegions();
        SpawnRegion region = regions.stream()
                .filter(r -> r.includesPoint(x, z))
                .findFirst()
                .orElseGet(() -> new SpawnRegion(x, z, SimpleCloudsConstants.SPAWN_RADIUS));

        CloudSpawningConfig config = spawnConfig;

        if (generator.getCloudsInRegion(region).size() > config.getMaxInitialRegions())
            return;

        for (int i = 0; i < config.getMaxInitialRegions(); i++) {
            int sharedRadius = BiasedToBottomInt.of(MIN_RADIUS, MAX_RADIUS).sample(random);

            for (int j = 0; j < SimpleCloudsConstants.SPAWN_ATTEMPTS; j++) {
                Vector2i pos;
                if (generator.getClouds().isEmpty()) {
                    sharedRadius = 200;
                    pos = new Vector2i(x, z);
                } else {
                    pos = SpawnRegion.getRandomPointInRegion(region, random);
                }

                if (generator.getCloudsInRegion(region).size() >= config.getMaxInitialRegions())
                    return;

                boolean intersectsOther = regions.stream()
                        .filter(r -> r != region)
                        .anyMatch(r -> r.includesPoint(pos.x, pos.y));
                if (intersectsOther)
                    continue;

                Map<RegionInstanceKey, Integer> regionsInArea = WeatherSampler.sampleRegionsInArea(pos.x, pos.y, sharedRadius, level);
                WeatherSampler.WeatherStats stats = WeatherSampler.computeWeatherStats(regionsInArea, level.getGameTime());
                if (stats == null)
                    continue;

                int severity = determineCloudSeverity(
                        stats.temperature(),
                        stats.humidity(),
                        stats.pressure(),
                        calculateDewPoint(stats.temperature(), stats.humidity()),
                        stats.stormFactor(),
                        level
                );
                if (severity <= 0) {
                    continue;
                }
                String cloudId = CloudLibrary.getCloudIdFromSeverity(severity);
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID, cloudId);
                CloudSpawningConfig.Info selected = config.getWeightInfo(rl);
                if (selected == null)
                    return;

                Optional<CloudRegion> cloudFormation = createRegion(
                        selected,
                        stats.dominantRegion(),
                        level,
                        random,
                        stats.windVector(),
                        generator
                );

                int finalSharedRadius = sharedRadius;
                cloudFormation.ifPresent(cf -> {
                    cf.setRadius(finalSharedRadius);
                    if (generator.addCloud(cf, CloudGenerator.Order.USE_WEIGHT)) {
                        SimpleCloudsTrackingIdentity.Entry identity = SimpleCloudsTrackingIdentity.resolve(cf, level);
                        SimpleCloudsRollbackDebugger.markSimpleCloudWrite("initial_generation_add", identity.trackingKey(), level);
                        setIsInit(true);
                    }
                });

                break;
            }
        }
    }

    public static void logDiagnostic(double x, double z, Level level) {
        SimpleCloudsRenderDiagnostics.logPlayerSample(level, x, z);
    }

    public static boolean isRainningAt(Level level,BlockPos pos) {
        return CloudManager.get(level).isRainingAt(pos);
    }

    // ---------------------------------------------------------------------
    // Misc helpers
    // ---------------------------------------------------------------------
    public static double getCloudScale() {
        return SimpleCloudsConstants.CLOUD_SCALE;
    }

    public static boolean isCloudAtPos(Level level, BlockPos pos) {
        return CloudManager.get(level).getCloudGenerator().getCloudAtWorldPosition(pos.getX() + 0.5F, pos.getZ() + 0.5F) !=null;
    }

    /** Samples Simple Clouds severity behind the optional service boundary. */
    public static int sampleSeverityAt(ServerLevel level, BlockPos pos) {
        int strongest = 1;
        for (CloudRegion region : CloudManager.get(level).getClouds()) {
            double dx = region.getWorldX() - pos.getX();
            double dz = region.getWorldZ() - pos.getZ();
            double radius = region.getRadius();
            if (dx * dx + dz * dz <= radius * radius) {
                strongest = Math.max(strongest,
                        CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId()));
            }
        }
        return strongest;
    }
}

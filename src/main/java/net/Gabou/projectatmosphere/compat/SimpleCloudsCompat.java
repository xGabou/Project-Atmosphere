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
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.WeatherSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector2i;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.Gabou.projectatmosphere.manager.SimpleCloudSpawner.calculateDewPoint;
import static net.Gabou.projectatmosphere.manager.SimpleCloudSpawner.determineCloudSeverity;

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
    public static void init(ServerLevel level) {
        cloudManager = (ServerCloudManager) CloudManager.get(level);
        generator = cloudManager.getCloudGenerator();
        spawnConfig = generator.getSpawnConfig().get();
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
    public static CloudRegion spawnCloudInBiome(String cloudId, BiomeInstanceKey key, ServerLevel level, @Nullable CloudRegion dummy, WindVector windVector) {
        if (key == null || key.samplePos() == null) {
            return null;
        }
        return spawnCloud(cloudId, key.samplePos(), key.biomeType(), level, dummy, windVector);
    }

    public static CloudRegion spawnCloudInRegion(String cloudId, RegionInstanceKey key, ServerLevel level, @Nullable CloudRegion dummy, WindVector windVector) {
        if (key == null) {
            return null;
        }
        return spawnCloud(cloudId, key.center(), null, level, dummy, windVector);
    }

    // ---------------------------------------------------------------------
    // Region creation
    // ---------------------------------------------------------------------
    private static CloudRegion spawnCloud(String cloudId, BlockPos anchor, @Nullable ResourceLocation biomeId, ServerLevel level, @Nullable CloudRegion dummy, WindVector windVector) {

        if (!isInit) {
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
            BiomeInstanceKey biomeKey,
            ServerLevel level,
            RandomSource random,
            WindVector wind,
            CloudGenerator generator
    ) {
        if (biomeKey == null || biomeKey.samplePos() == null) {
            return Optional.empty();
        }
        return createRegion(info, biomeKey.samplePos(), level, random, wind, generator);
    }

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
        cloudRegion.setRadius(ProjectAtmosphere.DEFAULT_REGION_RADIUS);

        return Optional.of(cloudRegion);
    }

    public static void doInitialGenWithWeather(int x, int z, ServerLevel level) {
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
                    generator.addCloud(cf, CloudGenerator.Order.USE_WEIGHT);
                    setIsInit(true);
                });

                break;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Misc helpers
    // ---------------------------------------------------------------------
    public static double getCloudScale() {
        return SimpleCloudsConstants.CLOUD_SCALE;
    }
}

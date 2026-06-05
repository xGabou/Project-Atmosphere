package net.Gabou.projectatmosphere.manager;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.modules.weather.WeatherSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import org.joml.Vector2i;

import java.util.*;

import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;

import static net.Gabou.projectatmosphere.compat.SimpleCloudsCompat.MAX_RADIUS;
import static net.Gabou.projectatmosphere.compat.SimpleCloudsCompat.MIN_RADIUS;
import static net.Gabou.projectatmosphere.manager.CloudSpawnSeverityRules.calculateDewPoint;
import static net.Gabou.projectatmosphere.manager.CloudSpawnSeverityRules.determineCloudSeverity;

public class SimpleCloudSpawner {

    private static final int SPAWN_INTERVAL_TICKS = 24000;

    private static long LAST_SPAWN_TICK = 0;


    private static int NB_MAX_CLOUDS_TYPES = 4;

    private static int currentViolence = 0;

    private static float DEW_GAP_MODIFIER = 1.0f;
    private static float PRESSURE_MODIFIER = 1.0f;
    private static float HUMIDITY_MODIFIER = 1.0f;
    private static float TEMPERATURE_MODIFIER = 1.0f;

    private static final float SUNNY_THRESHOLD = 0.45f;

    public static int getCurrentViolence() {
        return currentViolence;
    }

    private static int setViolence(int violence) {
        if (violence < 0 || violence > 7) {
            throw new IllegalArgumentException("Violence must be between 0 and 7");
        }
        return violence;
    }


    public static void trySpawnClouds(ServerLevel level, CloudGenerator generator) {
        List<SpawnRegion> spawnRegions = generator.getSpawnRegions();
        if (spawnRegions.isEmpty()) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] No spawn regions available");
            return;
        }

        RandomSource random = RandomSource.create();
        CloudSpawningConfig config = generator.getSpawnConfig().get();

        int currentCount = generator.getClouds().size();
        int maxRegions = config.getMaxInitialRegions();
        int remaining = maxRegions - currentCount;
        if (remaining <= 0) return;

        int toSpawn = Mth.clamp(BiasedToBottomInt.of(1, 5).sample(random), 1, remaining);

        for (int i = 0; i < toSpawn; i++) {
            SpawnRegion region = spawnRegions.get(random.nextInt(spawnRegions.size()));
            int radius = BiasedToBottomInt.of(MIN_RADIUS, MAX_RADIUS).sample(random);
            Vector2i point = SpawnRegion.getRandomPointInRegion(region, random);

            // Run the expensive biome/weather scan in the WEATHER pool
            AsyncAtmosphereService.runWithCallback(
                    PoolType.WEATHER,
                    () -> {
                        Map<RegionInstanceKey, Integer> sampledRegions = WeatherSampler.sampleRegionsInArea(point.x, point.y, radius, level);
                        WeatherSampler.WeatherStats stats = WeatherSampler.computeWeatherStats(sampledRegions, level.getGameTime());
                        if (stats == null) return null;

                        boolean isWinter = SeasonTimeHelper.stage(level) == SeasonStage.WINTER;
                        boolean freezing = stats.temperature() <= 0.0F;

                        int severity = determineCloudSeverity(
                                stats.temperature(),
                                stats.humidity(),
                                stats.pressure(),
                                calculateDewPoint(stats.temperature(), stats.humidity()),
                                stats.stormFactor(),
                                level
                        );
                        if (severity <= 0) return null;

                        boolean snowstorm = severity > 5 && freezing;
                        String cloudId;
                        if (snowstorm) {
                            cloudId = CloudLibrary.getSnowstormCloudId();
                        } else {
                            cloudId = CloudLibrary.getCloudIdFromSeverity(severity);
                            if (CloudLibrary.isThunderCloud(cloudId) && (isWinter || freezing)) {
                                cloudId = CloudLibrary.getCloudIdFromSeverity(5);
                            }
                        }

                        return new CloudSpawnRequest(stats, radius, cloudId);
                    },
                    request -> {
                        if (request == null) return;

                        // Back on the main server thread
                        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID, request.cloudId());
                        CloudSpawningConfig.Info info = config.getWeightInfo(rl);
                        if (info == null) {
                            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Unknown cloud type: {}", request.cloudId());
                            return;
                        }

                        Optional<CloudRegion> dummyOpt = SimpleCloudsCompat.createRegion(

                                info,
                                request.stats().dominantRegion(),
                                level,
                                random,
                                request.stats().windVector(),
                                generator
                        );
                        if (dummyOpt.isEmpty()) return;

                        CloudRegion dummy = dummyOpt.get();
                        dummy.setRadius(request.radius());

                        SimpleCloudsCompat.spawnCloudInRegion(
                                request.cloudId(),
                                request.stats().dominantRegion(),
                                level,
                                dummy,
                                request.stats().windVector()
                        );
                    }
            );
        }
    }

    // Data carrier between async and main thread
    record CloudSpawnRequest(WeatherSampler.WeatherStats stats, int radius, String cloudId) {
    }


    public static BlockPos getRandomPosInRegion(SpawnRegion region, RandomSource random, ServerLevel level) {
        Vector2i vec = SpawnRegion.getRandomPointInRegion(region, random);
        return new BlockPos(vec.x, level.getSeaLevel(), vec.y);
    }


    public static float calculateDewPoint(float temperature, float humidity) {
        return CloudSpawnSeverityRules.calculateDewPoint(temperature, humidity);
    }

    public static int determineCloudSeverity(
            float temperature,
            float humidity,
            float pressure,
            float dewPoint,
            float stormFactor,
            ServerLevel level
    ) {
        return CloudSpawnSeverityRules.determineCloudSeverity(temperature, humidity, pressure, dewPoint, stormFactor, level);
    }

    public static void spawnCloudForPlayer(ServerPlayer player, ServerLevel level) {
        RegionInstanceKey regionKey = RegionInstanceKey.from(player.blockPosition());
        SimpleCloudsCompat.spawnCloudInRegion("itty_bitty", regionKey, level, null, ForecastOrchestrator.getWind(regionKey, level.getGameTime()));
    }


}




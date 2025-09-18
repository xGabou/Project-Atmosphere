package net.Gabou.projectatmosphere.manager;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.storm.GlobalStormHistoryData;
import net.Gabou.projectatmosphere.modules.snowstorm.SnowstormManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.WeatherSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import org.joml.Vector2i;

import java.util.*;

import net.neoforged.fml.ModList;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

import static net.Gabou.projectatmosphere.compat.SimpleCloudsCompat.MAX_RADIUS;
import static net.Gabou.projectatmosphere.compat.SimpleCloudsCompat.MIN_RADIUS;

public class SimpleCloudSpawner {

    private static final int SPAWN_INTERVAL_TICKS = 24000;

    private static long LAST_SPAWN_TICK = 0;


    private static float PRESSION_MOYENNE = 1013.25f;

    private static int NB_MAX_CLOUDS_TYPES = 4;

    private static int currentViolence = 0;

    private static float DEW_GAP_MODIFIER = 1.0f;
    private static float PRESSURE_MODIFIER = 1.0f;
    private static float HUMIDITY_MODIFIER = 1.0f;
    private static float TEMPERATURE_MODIFIER = 1.0f;

    private static final float STORM_BIAS = 1.5f;
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


            AsyncAtmosphereService.runWithCallback(
                    PoolType.WEATHER,
                    () -> {
                        Set<BiomeInstanceKey> sample = WeatherSampler.sampleBiomesInArea(point.x, point.y, radius, level);
                        WeatherSampler.WeatherStats stats = WeatherSampler.computeWeatherStats(sample, level, level.getGameTime());
                        if (stats == null) return null;

                        boolean isWinter = SeasonHelper.getSeasonState(level).getSeason() == Season.WINTER;
                        boolean freezing = stats.temperature() <= 0.0F;

                        int severity = determineCloudSeverity(
                                stats.temperature(),
                                stats.humidity(),
                                stats.pressure(),
                                calculateDewPoint(stats.temperature(), stats.humidity()),
                                stats.stormChance(),
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

                        BiomeInstanceKey biomeKey = new BiomeInstanceKey(request.stats().dominantBiome(), request.stats().pos());
                        Optional<CloudRegion> dummyOpt = SimpleCloudsCompat.createRegion(

                                info,
                                biomeKey,
                                level,
                                random,
                                request.stats().windVector(),
                                generator
                        );
                        if (dummyOpt.isEmpty()) return;

                        CloudRegion dummy = dummyOpt.get();
                        dummy.setRadius(request.radius());

                        SimpleCloudsCompat.spawnCloudInBiome(
                                request.cloudId(),
                                biomeKey,
                                level,
                                dummy,
                                request.stats().windVector()
                        );
                    }
            );
        }
    }

    // Data carrier between async and main thread
    record CloudSpawnRequest(WeatherSampler.WeatherStats stats, int radius, String cloudId) {}


    public static BlockPos getRandomPosInRegion(SpawnRegion region, RandomSource random, ServerLevel level) {
        Vector2i vec = SpawnRegion.getRandomPointInRegion(region, random);
        return new BlockPos(vec.x, level.getSeaLevel(), vec.y);
    }


    public static float calculateDewPoint(float temperature, float humidity) {

        final float ACONST = 17.62f;
        final float BCONST = 243.12f;

        float result = (ACONST * temperature) / (BCONST + temperature) + (float) Math.log(humidity / 100.0f);
        return (BCONST * result) / (ACONST - result);
    }

    public static int determineCloudSeverity(float temperature, float humidity, float pressure, float dewPoint, float stormChance, ServerLevel level) {

        float dewGap = temperature - dewPoint;
        float pressureFactor = 1.0f - (pressure / PRESSION_MOYENNE);
        float humidityFactor = humidity / 100.0f;
        float tempIdealness = 1.0f - Math.abs(temperature - 15.0f) / 40.0f;

        float dewGapFactor = 1.0f - Math.min(dewGap, 10.0f) / 10.0f;

        float instability =
                (dewGapFactor * DEW_GAP_MODIFIER) +
                        (pressureFactor * PRESSURE_MODIFIER) +
                        (humidityFactor * HUMIDITY_MODIFIER) +
                        (tempIdealness * TEMPERATURE_MODIFIER);

        int currentDay = (int) (level.getDayTime() / 24000L);

        GlobalStormHistoryData data = GlobalStormHistoryData.get(level);
        int lastSevere = data.getLastSevereDay();
        int daysSince = (lastSevere == Integer.MIN_VALUE) ? Integer.MAX_VALUE : Math.max(0, currentDay - lastSevere);


        float boost = 1f + 0.08f * daysSince;  // +8% per day
        boost = Math.min(boost, 4f);         // cap at 2.5x
        float adjustedChance = Math.min(1f, stormChance * boost * STORM_BIAS);

        float rawScore = instability * adjustedChance;
        if (rawScore < SUNNY_THRESHOLD) {
            return 0;
        }

        int severity = Math.max(1, Math.min(7, Math.round(rawScore)));
        if (severity >= 5) {
            data.recordSevere(currentDay);
        }

        return severity;
    }

    public static void spawnCloudForPlayer(ServerPlayer player, ServerLevel level) {
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(player.blockPosition(), level), player.blockPosition());
        SimpleCloudsCompat.spawnCloudInBiome("itty_bitty", key, level, null, ForecastOrchestrator.getCurrentWind(key, level.getGameTime()));
    }


}

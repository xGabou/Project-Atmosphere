package net.Gabou.projectatmosphere.manager;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.storm.GlobalStormHistoryData;
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

    private static final float STORM_BIAS = 1.1f;
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
    record CloudSpawnRequest(WeatherSampler.WeatherStats stats, int radius, String cloudId) {
    }


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

    public static int determineCloudSeverity(
            float temperature,
            float humidity,
            float pressure,
            float dewPoint,
            float stormChance,
            ServerLevel level
    ) {
        float dewGap = Math.max(0f, temperature - dewPoint);
        float dewGapFactor = 1.0f - Math.min(dewGap / 12.0f, 1.0f);

        float pressureFactor = (PRESSION_MOYENNE - pressure) / 60.0f;
        pressureFactor = Math.max(-1f, Math.min(pressureFactor, 1f));

        float humidityFactor = humidity / 100.0f;
        float tempIdealness = 1.0f - Math.abs(temperature - 18.0f) / 45.0f;

        float instability = (dewGapFactor * 0.4f)
                + (pressureFactor * 0.25f)
                + (humidityFactor * 0.55f)
                + (tempIdealness * 0.3f);
        instability = Math.min(Math.max(instability, 0f), 1f);

        int currentDay = (int) (level.getDayTime() / 24000L);
        GlobalStormHistoryData data = GlobalStormHistoryData.get(level);

        int lastStrong = data.getLastSevereDay();
        int daysSince = (lastStrong == Integer.MIN_VALUE)
                ? Integer.MAX_VALUE : Math.max(0, currentDay - lastStrong);

        // === Check recent storm streak ===
        int recentStreak = data.getRecentSevereCount(); // track last consecutive severe storms
        int cooldown = data.getCooldownDaysRemaining();

        // If we hit 4 severe storms, start a cooldown of 3–5 days
        if (recentStreak >= 4 && cooldown <= 0) {
            cooldown = 3 + level.random.nextInt(3); // 3–5 days cooldown
            data.setCooldownDaysRemaining(cooldown);
        }

        // Decrease cooldown daily if active
        if (cooldown > 0 && daysSince > 0) {
            cooldown = Math.max(0, cooldown - daysSince);
            data.setCooldownDaysRemaining(cooldown);
        }

        // During cooldown, clamp stormChance and instability to reduce event frequency
        if (cooldown > 0) {
            stormChance *= 0.25f;
            instability *= 0.5f;
        }

        int severity = getSeverity(stormChance, daysSince, instability);

        // Record storm results
        if (severity >= 5) {
            data.recordSevere(currentDay);
        } else {
            data.resetIfCalm(currentDay);
        }

        return severity;
    }


    private static int getSeverity(float stormChance, int daysSince, float instability) {
        float boost = 0f;
        if (daysSince <= 2) {
            boost = 1f/(5-daysSince);
        } else {
            boost = 1f + 0.07f * daysSince;
            boost = Math.min(boost, 1.7f);
        }


        float adjustedChance = Math.min(1f, stormChance * boost * STORM_BIAS);

        // === Smooth Probability Curve ===
        return calculateSeverity(daysSince, instability, adjustedChance);
    }

    private static int calculateSeverity(int daysSince, float instability, float adjustedChance) {
        double weighted = instability * adjustedChance * AtmoCommonConfig.STORM_SEVERITY_BOOSTER.get();
        float raw = (float) (1.0 / (1.0 + Math.exp(-2.3 * (weighted - 1.0)))); // sigmoid

        // === Temporal Bias Toward 5 + Events ===
        // grows linearly up to 10 days, pushing score upward by ≤ 0.25
        float dayBias = Math.min(1f, daysSince / 10f);
        float biasAdjusted = raw + (0.25f * dayBias * (1f - raw));

        // Map 0–1 → 1–7
        int severity = (int) Math.floor(biasAdjusted * 6.0f) + 1;
        severity = Math.max(1, Math.min(7, severity));
        return severity;
    }

    public static void spawnCloudForPlayer(ServerPlayer player, ServerLevel level) {
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(player.blockPosition(), level), player.blockPosition());
        SimpleCloudsCompat.spawnCloudInBiome("itty_bitty", key, level, null, ForecastOrchestrator.getCurrentWind(key, level.getGameTime()));
    }


}

package net.Gabou.projectatmosphere.manager;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.async.BiomeSampler;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.ToughAsNailsCompat;
import net.Gabou.projectatmosphere.event.BiomeChangeManager;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.humidity.HumidityGenerator;
import net.Gabou.projectatmosphere.modules.pressure.PressureGenerator;
import net.Gabou.projectatmosphere.modules.sandStorm.SandStormAPI;
import net.Gabou.projectatmosphere.modules.storm.StormGenerator;
import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator;
import net.Gabou.projectatmosphere.modules.temperature.variation.VariationGenerator;
import net.Gabou.projectatmosphere.modules.wind.WindGenerator;
import net.Gabou.projectatmosphere.modules.wind.WindMath;
import net.Gabou.projectatmosphere.network.BiomeDayTemperaturePacket;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.tuple.Pair;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ForecastGenerator {

    private static final int DIFFUSION_RADIUS = 200;
    private static final float DIFFUSION_RATE = 0.1f;
    private static final int SAMPLE_STEP = 128;
    private static BiomeInstanceKey scheduledStormBiome = null;
    private static long scheduledStormTime = -1L;

    public static BiomeInstanceKey getScheduledSandstormBiome() {
        return scheduledStormBiome;
    }

    static long seed = 0L;

    private static final float SANDSTORM_WIND_THRESHOLD_BASE = 10f;
    private static final float SANDSTORM_WIND_THRESHOLD_MIN = 6f;

    private static final float SANDSTORM_HUMIDITY_THRESHOLD_BASE = 20f;
    private static final float SANDSTORM_HUMIDITY_THRESHOLD_MAX = 35f;

    private static final float SANDSTORM_PRESSURE_THRESHOLD_BASE = 1005f;
    private static final float SANDSTORM_PRESSURE_THRESHOLD_MAX = 1015f;

    private static final boolean sandStormLoaded = CompatHandler.isSandStormsLoaded();

    public static final int MAX_POSITIONS_PER_BIOME;

    public static final Set<ResourceLocation> SANDSTORM_BIOMES = Set.of(
            ResourceLocation.fromNamespaceAndPath("minecraft", "desert"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "badlands")
    );


    static {
        if (CompatHandler.isToughAsNailsLoaded()) {
            MAX_POSITIONS_PER_BIOME = 25;
        } else {
            MAX_POSITIONS_PER_BIOME = 40;
        }
    }

    static final int RADIUS = SimpleCloudsConstants.SPAWN_RADIUS;


    static final Set<BiomeInstanceKey> biomeSamples = ConcurrentHashMap.newKeySet();

    // Build once at startup or cache
    private static Map<ResourceLocation, List<BiomeInstanceKey>> biomeIndex = new ConcurrentHashMap<>();


    private static final Map<ResourceLocation, Integer> biomeSampleCounts = new ConcurrentHashMap<>();


    private static final Map<ResourceLocation, BiomeForecast> AVERAGE_FORECASTS = new ConcurrentHashMap<>();


    static final Map<BiomeInstanceKey, BiomeForecast> FORECAST_MAP = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, List<BiomeForecast>> grouped = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> REPORTED_MISSING_FORECASTS = ConcurrentHashMap.newKeySet();

    public static Map<ResourceLocation, List<BiomeInstanceKey>> getBiomeIndex() {
        return Collections.unmodifiableMap(biomeIndex);
    }

    public static void groupBiomeByType() {
        biomeIndex = biomeSamples.stream()
                .collect(Collectors.groupingBy(BiomeInstanceKey::biomeType));
    }

    /** Compute average forecasts for each biome type inside grouped and only for daily*/
    private static void computeAverageForecastsByBiomeType() {
        computeAverageDaily();

    }

    private static void computeAllAverage(){
        Map<ResourceLocation, float[]> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());
            avg.setTemperature(averageWeek(list, BiomeForecast::getTemperature));
            avg.setPressureDay(averageDay(list, BiomeForecast::getPressureDay));
            avg.setHumidity(averageWeek(list, BiomeForecast::getHumidity));
            avg.setPressure(averageWeek(list, BiomeForecast::getPressure));
            avg.setPressureTomorrow(averageDay(list, BiomeForecast::getPressureTomorrow));
            avg.setWind(averageWindWeek(list, BiomeForecast::getWind));
            avg.setTemperatureDay(averageDay(list, BiomeForecast::getTemperatureDay));
            map.put(entry.getKey(), avg.getTemperatureDay());
            avg.setTemperatureTomorrow(averageDay(list, BiomeForecast::getTemperatureTomorrow));
            avg.setBiomeKey(entry.getValue().get(0).getBiomeKey());
            avg.setStormChanceDay(averageDay(list, BiomeForecast::getStormChanceDay));
            avg.setStormChanceTomorrow(averageDay(list, BiomeForecast::getStormChanceTomorrow));
            avg.setWindDay(averageWind(list, BiomeForecast::getWindDay));
            avg.setWindTomorrow(averageWind(list, BiomeForecast::getWindTomorrow));
            avg.setHumidityDay(averageDay(list, BiomeForecast::getHumidityDay));
            avg.setHumidityTomorrow(averageDay(list, BiomeForecast::getHumidityTomorrow));

        }
        PacketDistributor.sendToAllPlayers(new BiomeDayTemperaturePacket(map));

    }

    private static void computeAverageDaily() {
        Map<ResourceLocation, float[]> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setTemperatureDay(averageDay(list, BiomeForecast::getTemperatureDay));
            map.put(entry.getKey(), avg.getTemperatureDay());
            avg.setTemperatureTomorrow(averageDay(list, BiomeForecast::getTemperatureTomorrow));
            avg.setBiomeKey(entry.getValue().get(0).getBiomeKey());

            avg.setHumidityDay(averageDay(list, BiomeForecast::getHumidityDay));
            avg.setHumidityTomorrow(averageDay(list, BiomeForecast::getHumidityTomorrow));
            avg.setPressureDay(averageDay(list, BiomeForecast::getPressureDay));
            avg.setPressureTomorrow(averageDay(list, BiomeForecast::getPressureTomorrow));
            avg.setWindDay(averageWind(list, BiomeForecast::getWindDay));
            avg.setWindTomorrow(averageWind(list, BiomeForecast::getWindTomorrow));
            avg.setStormChanceDay(averageDay(list, BiomeForecast::getStormChanceDay));
            avg.setStormChanceTomorrow(averageDay(list, BiomeForecast::getStormChanceTomorrow));



        }
        PacketDistributor.sendToAllPlayers(new BiomeDayTemperaturePacket(map));
    }

    private static void computeAverageTemperatureWeek() {
        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setTemperature(averageWeek(list, BiomeForecast::getTemperature));
        }
    }
    private static void computeAverageHumidityWeek() {
        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setHumidity(averageWeek(list, BiomeForecast::getHumidity));
        }
    }
    private static void computeAveragePressureWeek() {
        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setPressure(averageWeek(list, BiomeForecast::getPressure));
        }
    }
    private static void computeAverageWindWeek() {
        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setWind(averageWindWeek(list, BiomeForecast::getWind));
        }
    }
    private static void computeAverageStormChanceWeek() {
        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setStormChance(averageWeek(list, BiomeForecast::getStormChance));
        }
    }

    public static void groupForecastsByBiome() {
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            ResourceLocation biomeType = entry.getKey().biomeType();
            grouped.computeIfAbsent(biomeType, k -> new ArrayList<>()).add(entry.getValue());
        }
    }

    private static float interpolate(float base, float minOrMax, float chanceMax) {
        float t = Mth.clamp(chanceMax - 1.0f, 0f, 1f);
        return base - t * (base - minOrMax);
    }


    private static boolean shouldTriggerSandstorm(
            BiomeInstanceKey key,
            float[][] humidity,
            float[][] pressure,
            WindVector wind,
            float[] stormChance
    ) {
        if (!SANDSTORM_BIOMES.contains(key.biomeType())) return false;
        if (stormChance == null || stormChance.length < 2) return false;

        float chanceMax = stormChance[1];

        float todayHumidityMin = humidity[0][0];
        float todayPressureMin = pressure[0][0];
        float windSpeed = wind.gustSpeed();


        float humidityThreshold = interpolate(SANDSTORM_HUMIDITY_THRESHOLD_BASE, SANDSTORM_HUMIDITY_THRESHOLD_MAX, chanceMax);
        float pressureThreshold = interpolate(SANDSTORM_PRESSURE_THRESHOLD_BASE, SANDSTORM_PRESSURE_THRESHOLD_MAX, chanceMax);
        float windThreshold = interpolate(SANDSTORM_WIND_THRESHOLD_BASE, SANDSTORM_WIND_THRESHOLD_MIN, chanceMax);

        boolean dryEnough = todayHumidityMin < humidityThreshold;
        boolean windyEnough = windSpeed > windThreshold;
        boolean unstablePressure = todayPressureMin < pressureThreshold;

        return dryEnough && windyEnough && unstablePressure;
    }


    public static BiomeForecast getAverageForecast(ResourceLocation biomeType) {
        return AVERAGE_FORECASTS.get(biomeType);
    }

    public static Set<BiomeInstanceKey> getBiomeSamples() {
        return biomeSamples;
    }


    static void clearBiomeSamples() {
        biomeSamples.clear();
        biomeIndex.clear();
    }


    static void generateForecastForSavedRegion(ServerLevel level) {
        dailyAndSand(level);
    }

    private static void dailyAndSand(ServerLevel level) {
        DailyForecastGenerator.scheduleAll(level, FORECAST_MAP);

        computeAverageForecastsByBiomeType();
        FORECAST_MAP.forEach(ForecastPointerRegistry::setPointer);
    }

    /**
     * Generates a weekly forecast for the specified region centered at the given position.
     * This method samples biomes in a square area around the center and generates forecasts
     * based on the sampled biomes.
     *
     * @param center The center position of the region to sample.
     * @param level  The server level where the region is located.
     */
    static void generateForecastForRegion(BlockPos center, ServerLevel level) {
        final long start = System.nanoTime();

        // Fetch world-dependent values safely on main
        long day = AsyncAtmosphereService.callOnMainThread(
                () -> level.getDayTime() / 24000L
        );
        Season season = AsyncAtmosphereService.callOnMainThread(
                () -> SeasonHelper.getSeasonState(level).getSeason()
        );
        BiomeSource biomeSource = AsyncAtmosphereService.callOnMainThread(
                () -> level.getChunkSource().getGenerator().getBiomeSource()
        );

        // Collect biome samples
        BiomeSampler sampler = new BiomeSampler(ProjectAtmosphere.seed, level.registryAccess(),biomeSource);
        int sampleY = Math.max(level.getSeaLevel(), center.getY());
        for (int dx = -RADIUS; dx <= RADIUS; dx += SAMPLE_STEP) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += SAMPLE_STEP) {
                BlockPos samplePos = new BlockPos(center.getX() + dx, sampleY, center.getZ() + dz);
                ResourceLocation biomeId = sampler.getBiomeId(samplePos.getX(), samplePos.getY(), samplePos.getZ());
                if (biomeId.getPath().contains("cave")) continue;

                int count = biomeSampleCounts.getOrDefault(biomeId, 0);
                if (count >= MAX_POSITIONS_PER_BIOME) continue;

                biomeSamples.add(new BiomeInstanceKey(biomeId, samplePos));
                biomeSampleCounts.put(biomeId, count + 1);
            }
        }

        // Build biome index
        biomeIndex = biomeSamples.stream()
                .collect(Collectors.groupingBy(BiomeInstanceKey::biomeType));

        // Forecast generation
        if (CompatHandler.isToughAsNailsLoaded()) {
            Set<ResourceLocation> processed = new HashSet<>();
            for (BiomeInstanceKey key : biomeSamples) {
                ResourceLocation biomeId = key.biomeType();
                if (!processed.add(biomeId)) continue; // already handled this biome

                long sampleTime = System.currentTimeMillis();
                float[][] forecast = ToughAsNailsCompat.injectForecastForTAN(key, level);

                BiomeForecast bf = new BiomeForecast();
                bf.setTemperature(forecast);
                bf.setToughAsNailsFlag(true);
                bf.setBiomeKey(key);
                FORECAST_MAP.put(key, bf);

                long endTime = System.currentTimeMillis();
                ProjectAtmosphere.LOGGER.info(
                        "[Atmosphere] Tough as Nail forecast for " + biomeId + " at " + key.samplePos() +
                                " took " + (endTime - sampleTime) + " ms"
                );
            }
            groupForecastsByBiome();
        } else {
            for (BiomeInstanceKey key : biomeSamples) {
                BiomeForecast forecast = new BiomeForecast();
                forecast.setTemperature(generateTemperature(key, level));
                forecast.setBiomeKey(key);
                FORECAST_MAP.put(key, forecast);
            }
            groupForecastsByBiome();
            diffuseAndSmoothField(BiomeForecast::getTemperature, BiomeForecast::setTemperature);
        }

        computeAverageTemperatureWeek();

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setHumidity(generateHumidity(entry.getKey(), level, day));
        }
        diffuseAndSmoothField(BiomeForecast::getHumidity, BiomeForecast::setHumidity);
        computeAverageHumidityWeek();

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setPressure(generatePressure(entry.getKey(), day));
        }
        diffuseAndSmoothField(BiomeForecast::getPressure, BiomeForecast::setPressure);
        computeAveragePressureWeek();

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setWind(generateWind(entry.getKey()));
        }
        computeAverageWindWeek();
        computeAverageForecastsByBiomeType();

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setStormChance(generateStorm(
                    entry.getKey(),
                    level,
                    entry.getValue().getTemperature(),
                    entry.getValue().getHumidity(),
                    entry.getValue().getPressure(),
                    entry.getValue().getWind(),
                    season
            ));
        }
        computeAverageStormChanceWeek();

        dailyAndSand(level);

        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Forecast region generation took " + durationMs + " ms.");
    }

    private static float[][] generateStorm(BiomeInstanceKey key, ServerLevel level, float[][] temperature, float[][] humidity, float[][] pressure, WindVector[] wind, Season season) {
        return StormGenerator.generateWeeklyStormProfile(key, temperature, humidity, pressure, wind, season);
    }


    private static float[][] generateTemperature(BiomeInstanceKey key, ServerLevel level) {
        return SpikeManager.applySpikeLogic(key,
                VariationGenerator.applyVariationToWeek(
                        TemperatureGenerator.generateWeekForecast(level, key.samplePos(), key.biomeType())
                ));
    }

    private static float[][] generateHumidity(BiomeInstanceKey key, ServerLevel level, Long day) {
        return HumidityGenerator.generateWeekForecast(level, key, day);
    }

    private static float[][] generatePressure(BiomeInstanceKey key, Long day) {
        return PressureGenerator.generateWeekForecast(key, day);
    }

    private static WindVector[] generateWind(BiomeInstanceKey key) {
        return WindGenerator.generateWindWeek(key);
    }


    public static Map<BiomeInstanceKey, BiomeForecast> getForecastMap() {
        return FORECAST_MAP;
    }
    // Build once at initialization
    private static final Map<BiomeInstanceKey, List<BiomeInstanceKey>> NEIGHBOR_CACHE = new ConcurrentHashMap<>();

    private static void buildNeighborCache(Map<BiomeInstanceKey, float[][]> original, long threshold) {
        for (BiomeInstanceKey a : original.keySet()) {
            BlockPos pa = a.samplePos();
            List<BiomeInstanceKey> neighbors = new ArrayList<>();
            for (BiomeInstanceKey b : original.keySet()) {
                if (a == b) continue;
                if (pa.distSqr(b.samplePos()) <= threshold) {
                    neighbors.add(b);
                }
            }
            NEIGHBOR_CACHE.put(a, neighbors);
        }
    }


    private static void diffuseAndSmoothField(Function<BiomeForecast, float[][]> getter,
                                              BiConsumer<BiomeForecast, float[][]> setter) {
        long threshold = DIFFUSION_RADIUS * DIFFUSION_RADIUS;

        // 1. Collect original values
        Map<BiomeInstanceKey, float[][]> original = new HashMap<>();
        for (var entry : FORECAST_MAP.entrySet()) {
            float[][] data = getter.apply(entry.getValue());
            if (data != null) {
                original.put(entry.getKey(), data);
            }
        }

        // 2. Build neighbor cache only if empty or outdated
        if (NEIGHBOR_CACHE.isEmpty() || NEIGHBOR_CACHE.size() != original.size()) {
            buildNeighborCache(original, threshold);
        }

        Map<BiomeInstanceKey, float[][]> diffused = new HashMap<>();

        // 3. Use precomputed neighbors
        for (var entry : original.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            float[][] week = entry.getValue();

            List<BiomeInstanceKey> neighbors = NEIGHBOR_CACHE.getOrDefault(key, List.of());
            if (neighbors.isEmpty()) {
                diffused.put(key, week);
                continue;
            }

            float[][] smoothed = new float[7][2];
            for (int d = 0; d < 7; d++) {
                for (int i = 0; i < 2; i++) {
                    float val = week[d][i];
                    float sum = 0, count = 0;
                    for (BiomeInstanceKey nKey : neighbors) {
                        float[][] n = original.get(nKey);
                        sum += n[d][i];
                        count++;
                    }
                    float avg = sum / count;
                    smoothed[d][i] = val + DIFFUSION_RATE * (avg - val);
                }
            }

            diffused.put(key, smoothed);
        }

        // 4. Temporal smoothing and commit back
        for (var entry : diffused.entrySet()) {
            float[][] week = entry.getValue();
            for (int d = 0; d < 7; d++) {
                float[] prev = (d > 0) ? week[d - 1] : week[d];
                float[] curr = week[d];
                float[] next = (d < 6) ? week[d + 1] : week[d];
                for (int i = 0; i < 2; i++) {
                    curr[i] = (prev[i] + 2 * curr[i] + next[i]) / 4f;
                }
            }

            BiomeForecast forecast = FORECAST_MAP.get(entry.getKey());
            if (forecast != null) {
                setter.accept(forecast, week);
            }
        }
    }


    static void clearForecasts() {
        FORECAST_MAP.clear();
        grouped.clear();
        biomeSamples.clear();
        biomeIndex.clear();
        biomeSampleCounts.clear();
        AVERAGE_FORECASTS.clear();
        REPORTED_MISSING_FORECASTS.clear();
        scheduledStormBiome = null;
        scheduledStormTime = -1L;
        ForecastPointerRegistry.clear();
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Cleared all forecasts and samples.");
    }

    static void putForecast(BiomeInstanceKey key, BiomeForecast forecast) {
        FORECAST_MAP.put(key, forecast);
        if (biomeSamples.add(key)) {
            biomeSampleCounts.put(key.biomeType(), biomeSampleCounts.getOrDefault(key.biomeType(), 0) + 1);
        }

    }

    static BiomeForecast getForecast(BiomeInstanceKey key) {
        return FORECAST_MAP.get(key);
    }


    static float getHumidityValue(BiomeInstanceKey key, long tick) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.HUMIDITY);
        if (forecast == null) return 0.0f;

        float[] curve = forecast.getHumidityDay();
        int minuteOfDay = (int) ((tick % 24000L) / 100L);
        if(curve==null)return 50f;
        return curve[Math.min(minuteOfDay, curve.length - 1)];
    }

    static float getTemperatureValue(BiomeInstanceKey key, long tick) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.TEMPERATURE);
        if (forecast == null) return 0.0f;

        float[] curve = forecast.getTemperatureDay();
        int minuteOfDay = (int) ((tick % 24000L) / 100L);
        if(curve==null)return 0.0f;
        return curve[Math.min(minuteOfDay, curve.length - 1)];
    }

    static float getStormChanceValue(BiomeInstanceKey key, long tick) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.STORM);
        if (forecast == null) return 0.0f;
        float[] curve = forecast.getStormChanceDay();
        int minuteOfDay = (int) ((tick % 24000L) / 100L);
        if(curve==null)return 0.0f;
        return curve[Math.min(minuteOfDay, curve.length - 1)];
    }

    static float getPressureValue(BiomeInstanceKey key, long tick) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.PRESSURE);
        if (forecast == null) return 0.0f;

        float[] curve = forecast.getPressureDay();
        int minuteOfDay = (int) ((tick % 24000L) / 100L);
        if(curve==null)return 1013f;
        return curve[Math.min(minuteOfDay, curve.length - 1)];
    }

    static WindVector getWindValue(BiomeInstanceKey key, long worldTime) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.WIND);
        if (forecast == null) return WindVector.fromBase(0, 0);

        WindVector original = forecast.getWindDay();
        if (original == null) return WindVector.fromBase(0, 0);


        float speed = WindMath.getSmoothGustedSpeed(original, worldTime);

        return new WindVector(speed, original.angleRadians(), original.gustSpeed());
    }

    public static BiomeForecast getClosestValidForecast(BiomeInstanceKey key, ForecastType type) {
        if (key == null) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Requested forecast with null biome key for type {}", type);
            return buildFallbackForecast(null);
        }

        BiomeForecast pointer = ForecastPointerRegistry.getPointer(key);
        if (pointer != null) {
            return pointer;
        }

        BiomeForecast average = getAverageForecast(key.biomeType());
        if (average != null) {
            return average;
        }

        warnMissingForecastOnce(key);
        return buildFallbackForecast(key);
    }

    private static void warnMissingForecastOnce(BiomeInstanceKey key) {
        if (key == null || key.biomeType() == null) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] No forecast data available for {}. Returning fallback.", key);
            return;
        }
        if (REPORTED_MISSING_FORECASTS.add(key.biomeType())) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] No forecast data available for {}. Returning fallback.", key);
        }
    }

    private static BiomeForecast buildFallbackForecast(BiomeInstanceKey key) {
        BiomeForecast fallback = new BiomeForecast();
        fallback.setBiomeKey(key);
        fallback.setTemperature(new float[7][2]);
        fallback.setHumidity(new float[7][2]);
        fallback.setPressure(new float[7][2]);
        WindVector[] windWeek = new WindVector[7];
        Arrays.fill(windWeek, WindVector.fromBase(0, 0));
        fallback.setWind(windWeek);
        fallback.setWindDay(WindVector.fromBase(0, 0));
        return fallback;
    }


//    public static BiomeForecast getClosestValidForecast(BiomeInstanceKey key, ForecastType type) {
//        BiomeForecast direct = FORECAST_MAP.get(key);
//        if (direct != null && direct.hasData(type)) {
//            return direct;
//        }
//
//
//
//
//        BiomeForecast closestSame = null;
//        double minDistSame = Double.MAX_VALUE;
//
//
//        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
//            BiomeInstanceKey otherKey = entry.getKey();
//            BiomeForecast forecast = entry.getValue();
//
//            if (!forecast.hasData(type)) continue;
//            if (!otherKey.biomeType().equals(key.biomeType())) continue;
//
//            double dist = otherKey.samplePos().distSqr(key.samplePos());
//            if (dist < minDistSame) {
//                minDistSame = dist;
//                closestSame = forecast;
//                if (dist < SAMPLE_STEP * 2) break;
//            }
//        }
//
//        if (closestSame != null) return closestSame;
//
//
//        BiomeForecast avg = AVERAGE_FORECASTS.get(key.biomeType());
//        if (avg != null && avg.hasData(type)) {
//            return avg;
//        }
//
//        BiomeForecast closestFallback = null;
//        double minDistAny = Double.MAX_VALUE;
//
//        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
//            BiomeForecast forecast = entry.getValue();
//            if (!forecast.hasData(type)) continue;
//
//            double dist = entry.getKey().samplePos().distSqr(key.samplePos());
//            if (dist < minDistAny) {
//                minDistAny = dist;
//                closestFallback = forecast;
//            }
//        }
//
//        return closestFallback;
//    }


    static void swapToTomorrow() {
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeForecast forecast = entry.getValue();


            forecast.setTemperature(rotateWeek(forecast.getTemperature()));
            forecast.setHumidity(rotateWeek(forecast.getHumidity()));
            forecast.setPressure(rotateWeek(forecast.getPressure()));
            forecast.setStormChance(rotateWeek(forecast.getStormChance()));
            forecast.setWind(rotateWindWeek(forecast.getWind()));


            if (forecast.getTemperatureTomorrow() != null) {
                forecast.setTemperatureDay(forecast.getTemperatureTomorrow());
            }

            if (forecast.getHumidityTomorrow() != null) {
                forecast.setHumidityDay(forecast.getHumidityTomorrow());
            }

            if (forecast.getPressureTomorrow() != null) {
                forecast.setPressureDay(forecast.getPressureTomorrow());
            }

            if (forecast.getStormChanceTomorrow() != null) {
                forecast.setStormChanceDay(forecast.getStormChanceTomorrow());
            }

            if (forecast.getWindTomorrow() != null) {
                forecast.setWindDay(forecast.getWindTomorrow());
            }
        }

        computeAllAverage();
    }

    private static float[][] rotateWeek(float[][] original) {
        if (original == null || original.length < 2) return original;

        int len = original.length;
        float[][] rotated = new float[len][2];

        for (int i = 0; i < len - 1; i++) {
            rotated[i] = original[i + 1];
        }


        rotated[len - 1] = new float[]{0f, 0f};
        return rotated;
    }

    private static WindVector[] rotateWindWeek(WindVector[] original) {
        if (original == null || original.length < 2) return original;

        int len = original.length;
        WindVector[] rotated = new WindVector[len];

        for (int i = 0; i < len - 1; i++) {
            rotated[i] = original[i + 1];
        }

        rotated[len - 1] = WindVector.fromBase(0, 0);
        return rotated;
    }


    private static float[][] averageWeek(List<BiomeForecast> forecasts, java.util.function.Function<BiomeForecast, float[][]> extractor) {
        int days = 7, cols = 2;
        float[][] result = new float[days][cols];

        for (BiomeForecast f : forecasts) {
            float[][] data = extractor.apply(f);
            if (data == null) continue;

            for (int d = 0; d < days; d++) {
                for (int c = 0; c < cols; c++) {
                    result[d][c] += data[d][c];
                }
            }
        }

        int size = forecasts.size();
        for (int d = 0; d < days; d++) {
            for (int c = 0; c < cols; c++) {
                result[d][c] /= size;
            }
        }

        return result;
    }

    private static float[] averageDay(List<BiomeForecast> forecasts, Function<BiomeForecast, float[]> extractor) {
        int size = forecasts.size();
        if (size == 0) return new float[0];

        int length = 240;
        float[] result = new float[length];

        for (BiomeForecast f : forecasts) {
            float[] curve = extractor.apply(f);
            if (curve == null || curve.length != length) continue;

            for (int i = 0; i < length; i++) {
                result[i] += curve[i];
            }
        }

        for (int i = 0; i < length; i++) {
            result[i] /= size;
        }

        return result;
    }

    private static WindVector averageWind(List<BiomeForecast> forecasts, Function<BiomeForecast, WindVector> extractor) {
        if (forecasts.isEmpty()) return WindVector.fromBase(0, 0);

        float sumX = 0;
        float sumZ = 0;
        float sumGust = 0;


        for (BiomeForecast f : forecasts) {
            WindVector wind = f.getWindDay();
            if (wind == null) continue;

            float angle = wind.angleRadians();
            float speed = wind.baseSpeed();

            sumX += speed * (float) Math.cos(angle);
            sumZ += speed * (float) Math.sin(angle);
            sumGust += wind.gustSpeed();

        }

        int size = forecasts.size();
        float avgX = sumX / size;
        float avgZ = sumZ / size;

        float avgSpeed = (float) Math.sqrt(avgX * avgX + avgZ * avgZ);
        float avgAngle = (float) Math.atan2(avgZ, avgX);
        float avgGust = sumGust / size;

        return new WindVector(avgSpeed, avgAngle, avgGust);
    }

    private static WindVector[] averageWindWeek(List<BiomeForecast> forecasts, Function<BiomeForecast, WindVector[]> extractor) {
        WindVector[] result = new WindVector[7];

        if (forecasts.isEmpty()) {
            Arrays.fill(result, WindVector.fromBase(0, 0));
            return result;
        }

        for (int day = 0; day < 7; day++) {
            float sumX = 0f;
            float sumZ = 0f;
            float sumGust = 0f;
            int count = 0;

            for (BiomeForecast forecast : forecasts) {
                WindVector[] windWeek = extractor.apply(forecast);
                if (windWeek == null || windWeek.length != 7) continue;

                WindVector wind = windWeek[day];
                if (wind == null) continue;

                float speed = wind.baseSpeed();
                float angle = wind.angleRadians();
                float gust = wind.gustSpeed();

                sumX += speed * (float) Math.cos(angle);
                sumZ += speed * (float) Math.sin(angle);
                sumGust += gust;
                count++;
            }

            if (count > 0) {
                float avgX = sumX / count;
                float avgZ = sumZ / count;
                float avgSpeed = (float) Math.sqrt(avgX * avgX + avgZ * avgZ);
                float avgAngle = (float) Math.atan2(avgZ, avgX);
                float avgGust = sumGust / count;
                result[day] = new WindVector(avgSpeed, avgAngle, avgGust);
            } else {
                result[day] = WindVector.fromBase(0, 0);
            }
        }

        return result;
    }




}

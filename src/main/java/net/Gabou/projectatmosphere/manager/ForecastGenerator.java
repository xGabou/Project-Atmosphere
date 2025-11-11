package net.Gabou.projectatmosphere.manager;

import com.BreadRes.desertstormwarming.logic.SandstormPhase;
import com.BreadRes.desertstormwarming.sounds.SandstormSounds;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.async.BiomeSampler;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.ToughAsNailsCompat;
import net.Gabou.projectatmosphere.event.BiomeChangeManager;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.humidity.HumidityGenerator;
import net.Gabou.projectatmosphere.modules.pressure.PressureGenerator;
import net.Gabou.projectatmosphere.modules.sandStorm.SandStormAPI;
import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator;
import net.Gabou.projectatmosphere.modules.temperature.variation.VariationGenerator;
import net.Gabou.projectatmosphere.modules.wind.WindGenerator;
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
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraftforge.network.PacketDistributor;
import org.apache.commons.lang3.tuple.Pair;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ForecastGenerator {

    private static final int SAMPLE_STEP = 128;
    private static BiomeInstanceKey scheduledStormBiome = null;
    private static SandstormPhase scheduledStormPhase = null;
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
    private static final Set<BiomeInstanceKey> SANDSTORM_FORECASTS = ConcurrentHashMap.newKeySet();

    public static Set<BiomeInstanceKey> getSandstormForecasts() {
        return Collections.unmodifiableSet(SANDSTORM_FORECASTS);
    }

    private static void clearSandstormForecasts() {
        SANDSTORM_FORECASTS.clear();
    }


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

    public static Map<ResourceLocation, List<BiomeInstanceKey>> getBiomeIndex() {
        return Collections.unmodifiableMap(biomeIndex);
    }

    public static void groupBiomeByType() {
        biomeIndex = biomeSamples.stream()
                .collect(Collectors.groupingBy(BiomeInstanceKey::biomeType));
    }

    private static void computeAverageForecastsByBiomeType() {
        Map<ResourceLocation, float[]> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());
            avg.setTemperature(averageWeek(list, BiomeForecast::getTemperature));
            avg.setHumidity(averageWeek(list, BiomeForecast::getHumidity));
            avg.setPressure(averageWeek(list, BiomeForecast::getPressure));
            avg.setWind(averageWindWeek(list, BiomeForecast::getWind));
            avg.setBiomeKey(list.get(0).getBiomeKey());

            float representative = deriveRepresentativeTemperature(avg);
            map.put(entry.getKey(), buildFlatCurve(representative));
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new BiomeDayTemperaturePacket(map));
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
    public static void groupForecastsByBiome() {
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            ResourceLocation biomeType = entry.getKey().biomeType();
            grouped.computeIfAbsent(biomeType, k -> new ArrayList<>()).add(entry.getValue());
        }
    }

    private static boolean shouldTriggerSandstorm(
            BiomeInstanceKey key,
            float[][] humidity,
            float[][] pressure,
            WindVector wind
    ) {
        if (!SANDSTORM_BIOMES.contains(key.biomeType())) return false;

        if (humidity == null || humidity.length == 0 || humidity[0].length == 0) return false;
        if (pressure == null || pressure.length == 0 || pressure[0].length == 0) return false;
        if (wind == null) return false;

        float todayHumidityMin = humidity[0][0];
        float todayPressureMin = pressure[0][0];
        float windSpeed = wind.gustSpeed();

        float dryness = Math.max(0f, 1f - (todayHumidityMin / SANDSTORM_HUMIDITY_THRESHOLD_MAX));
        float gustiness = Math.max(0f, (windSpeed - SANDSTORM_WIND_THRESHOLD_MIN) /
                Math.max(1f, SANDSTORM_WIND_THRESHOLD_BASE - SANDSTORM_WIND_THRESHOLD_MIN));
        float pressureDrop = Math.max(0f, (SANDSTORM_PRESSURE_THRESHOLD_BASE - todayPressureMin) / 20f);

        float severity = (dryness * 0.5f) + (gustiness * 0.3f) + (pressureDrop * 0.2f);

        boolean dryEnough = todayHumidityMin < SANDSTORM_HUMIDITY_THRESHOLD_BASE || dryness > 0.6f;
        boolean windyEnough = windSpeed > SANDSTORM_WIND_THRESHOLD_BASE * 0.85f;
        boolean unstablePressure = todayPressureMin < SANDSTORM_PRESSURE_THRESHOLD_BASE + 2f;

        return dryEnough && windyEnough && unstablePressure && severity > 0.55f;
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
        clearSandstormForecasts();

        FORECAST_MAP.entrySet().stream()
                .filter(entry -> SANDSTORM_BIOMES.contains(entry.getKey().biomeType()))
                .filter(entry -> {
                    BiomeForecast f = entry.getValue();
                    return f.getHumidity() != null && f.getHumidity().length > 0
                            && f.getPressure() != null && f.getPressure().length > 0
                            && f.getWind() != null && f.getWind().length > 0;
                })
                .filter(entry -> shouldTriggerSandstorm(
                        entry.getKey(),
                        entry.getValue().getHumidity(),
                        entry.getValue().getPressure(),
                        entry.getValue().getWind()[0]
                ))
                .forEach(entry -> {
                    BiomeInstanceKey key = entry.getKey();
                    entry.getValue().setSandstormExpected(true);
                    SANDSTORM_FORECASTS.add(key);
                });


        FORECAST_MAP.forEach((key, forecast) -> {
            AtmosphericStateRegistry.initializeState(key, forecast);
            ForecastOrchestrator.generateWindForecast(key, forecast);
        });
        AtmosphericStateRegistry.rebuildNeighbors();

        computeAverageForecastsByBiomeType();
        FORECAST_MAP.forEach(ForecastPointerRegistry::setPointer);

        if(!sandStormLoaded)return;
        if (!SandStormAPI.isSandstormActive() && scheduledStormBiome == null && !SANDSTORM_FORECASTS.isEmpty() ) {
            BiomeInstanceKey selected = SANDSTORM_FORECASTS.stream()
                    .skip(level.random.nextInt(SANDSTORM_FORECASTS.size()))
                    .findFirst()
                    .orElse(null);

            if (selected != null) {
                BiomeForecast forecast = FORECAST_MAP.get(selected);
                if (forecast != null) {
                    long baseTime = (level.getDayTime() / 24000L) * 24000L;
                    long randomOffset = 1000 + level.random.nextInt(9000);

                    scheduledStormBiome = selected;
                    scheduledStormPhase = computeStormPhase(forecast);
                    scheduledStormTime = baseTime + randomOffset;

                    ProjectAtmosphere.LOGGER.info("[Atmosphere] Scheduled sandstorm at tick {} in biome {} (phase: {})",
                            scheduledStormTime, selected.biomeType(), scheduledStormPhase);
                    for (ServerPlayer player : level.players()) {


                        boolean lastBiomeFlag = BiomeChangeManager
                                .getLastBiome()
                                .getOrDefault(player.getUUID(), Pair.of(null, false))
                                .getValue();

                        if (!lastBiomeFlag) {
                            for (SoundEvent soundEvent : SandstormSounds.getSoundsForPhase(SandStormAPI.getSandstormPhase())) {
                                Minecraft.getInstance().getSoundManager().stop(soundEvent.getLocation(), null);
                            }
                        }


                    }
                }
            }
        }
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
        for (int dx = -RADIUS; dx <= RADIUS; dx += SAMPLE_STEP) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += SAMPLE_STEP) {
                BlockPos samplePos = center.offset(dx, 0, dz);
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
                putForecast(key, bf);

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
                putForecast(key, forecast);
            }
            groupForecastsByBiome();
        }

        computeAverageTemperatureWeek();

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setHumidity(generateHumidity(entry.getKey(), level, day));
        }
        computeAverageHumidityWeek();

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setPressure(generatePressure(entry.getKey(), day));
        }
        computeAveragePressureWeek();

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setWind(generateWind(entry.getKey()));
        }
        computeAverageWindWeek();

        dailyAndSand(level);

        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Forecast region generation took " + durationMs + " ms.");
    }


    private static int tickCounter = 0;

    static void tickSandstormScheduler(ServerLevel level) {

        if (scheduledStormBiome != null && level.getDayTime() >= scheduledStormTime) {
            SandStormAPI. startSandstorm(scheduledStormPhase, scheduledStormBiome);


            ProjectAtmosphere.LOGGER.info("[Atmosphere] Triggered sandstorm in biome {} with phase {}",
                    scheduledStormBiome.biomeType(), scheduledStormPhase);

            scheduledStormBiome = null;
            scheduledStormTime = -1L;
            scheduledStormPhase = null;
        }
        if (SandStormAPI.isSandstormActive() && tickCounter % 50 == 0) {
            var sandStorms = SandStormAPI.getScheduledStormBiome();
            if (sandStorms.isEmpty()) {
                ProjectAtmosphere.LOGGER.warn("[Atmosphere] No sandstorm biomes found, but storm is active!");
                return;
            }
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Sandstorm active in {} biomes: {}", sandStorms.size(), sandStorms);
            AsyncAtmosphereService.runStorm(() -> {
                for (BiomeInstanceKey biome : sandStorms) {

                    SandStormAPI.blowSandInBiome(level,
                            biome,
                            getWindValue(biome, level.getDayTime()));

                }
            });
            tickCounter = 0;

        }
        tickCounter++;
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
    static void clearForecasts() {
        FORECAST_MAP.clear();
        grouped.clear();
        biomeSamples.clear();
        biomeIndex.clear();
        biomeSampleCounts.clear();
        AVERAGE_FORECASTS.clear();
        AtmosphericStateRegistry.clear();
        clearSandstormForecasts();
        scheduledStormBiome = null;
        scheduledStormPhase = null;
        scheduledStormTime = -1L;
        tickCounter = 0;
        ForecastPointerRegistry.clear();
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Cleared all forecasts and samples.");
    }

    static void putForecast(BiomeInstanceKey key, BiomeForecast forecast) {
        FORECAST_MAP.put(key, forecast);
        if (biomeSamples.add(key)) {
            biomeSampleCounts.put(key.biomeType(), biomeSampleCounts.getOrDefault(key.biomeType(), 0) + 1);
        }
        AtmosphericStateRegistry.initializeState(key, forecast);

    }


    static BiomeForecast getForecast(BiomeInstanceKey key) {
        return FORECAST_MAP.get(key);
    }



    static float getHumidityValue(BiomeInstanceKey key, long tick) {
        var state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            return 0.0f;
        }
        return state.getHumidityPercent();
    }

    static float getTemperatureValue(BiomeInstanceKey key, long tick) {
        var state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            return 0.0f;
        }
        return state.getTemperature();
    }

    static float getPressureValue(BiomeInstanceKey key, long tick) {
        var state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            return 0.0f;
        }
        return state.getPressure();
    }

    static WindVector getWindValue(BiomeInstanceKey key, long worldTime) {
        var state = AtmosphericStateRegistry.getState(key);
        if (state == null || state.getWind() == null) {
            return WindVector.fromBase(0, 0);
        }
        return state.getWind();
    }

    public static BiomeForecast getClosestValidForecast(BiomeInstanceKey key, ForecastType type) {
        return ForecastPointerRegistry.getPointer(key);
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


    private static SandstormPhase computeStormPhase(BiomeForecast forecast) {
        float wind = forecast.getWind()[0].baseSpeed();
        float pressure = forecast.getPressure()[0][0];
        float humidity = forecast.getHumidity()[0][0];

        if (wind > 35 && pressure < 980 && humidity < 0.15f) return SandstormPhase.PHASE_5;
        if (wind > 30) return SandstormPhase.PHASE_4;
        if (wind > 25) return SandstormPhase.PHASE_3;
        if (wind > 20) return SandstormPhase.PHASE_2;
        return SandstormPhase.PHASE_1;
    }



    private static float deriveRepresentativeTemperature(BiomeForecast avg) {
        if (avg.getBiomeKey() != null) {
            var state = AtmosphericStateRegistry.getState(avg.getBiomeKey());
            if (state != null) {
                return state.getTemperature();
            }
        }
        return averageDailyMidpoint(avg.getTemperature());
    }

    private static float[] buildFlatCurve(float value) {
        float[] arr = new float[24];
        Arrays.fill(arr, value);
        return arr;
    }

    private static float averageDailyMidpoint(float[][] week) {
        if (week == null || week.length == 0) {
            return 0f;
        }
        float sum = 0f;
        int count = 0;
        for (float[] day : week) {
            if (day == null || day.length == 0) continue;
            if (day.length == 1) {
                sum += day[0];
            } else {
                sum += (day[0] + day[Math.min(1, day.length - 1)]) * 0.5f;
            }
            count++;
        }
        return count == 0 ? 0f : sum / count;
    }

}

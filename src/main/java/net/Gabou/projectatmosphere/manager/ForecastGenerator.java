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
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ForecastGenerator {

    private static final int SAMPLE_STEP = 128;
    static long seed = 0L;
    static final boolean sandStormLoaded = CompatHandler.isSandStormsLoaded();
    public static final int MAX_POSITIONS_PER_BIOME;

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

    static void computeAverageForecastsByBiomeType() {
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
        SandStormManager.dailyAndSand(level);
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
        SeasonStage season = SeasonTimeHelper.stage(level);
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

        SandStormManager.dailyAndSand(level);

        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Forecast region generation took " + durationMs + " ms.");
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
        SandStormManager.clearSandstormForecasts();
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
        if (state != null) {
            return state.getTemperature();
        }

        BiomeForecast fallback = getClosestValidForecast(key, ForecastType.TEMPERATURE);
        if (fallback != null) {
            float[] curve = fallback.getTemperatureDay();
            if (curve != null && curve.length > 0) {
                int minuteOfDay = (int) ((tick % 24000L) / 100L);
                return curve[Math.min(minuteOfDay, curve.length - 1)];
            }

            float[][] week = fallback.getTemperature();
            if (week != null && week.length > 0) {
                return averageDailyMidpoint(week);
            }
        }

        return 0.0f;
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

        ProjectAtmosphere.LOGGER.warn("[Atmosphere] No forecast data available for {}. Returning fallback.", key);
        return buildFallbackForecast(key);
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

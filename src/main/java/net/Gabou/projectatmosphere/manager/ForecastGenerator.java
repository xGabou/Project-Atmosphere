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
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
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
import net.Gabou.projectatmosphere.util.DelayedTaskScheduler;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

    private static final int SAMPLE_STEP = 64;
    static long seed = 0L;
    static final boolean sandStormLoaded = CompatHandler.isSandStormsLoaded();
    public static final int MAX_POSITIONS_PER_BIOME;
    static final float SEA_LEVEL = AsyncAtmosphereService.callOnMainThread(ProjectAtmosphere::getSeaLevel);

    public static String message = "";

    static {
        if (CompatHandler.isToughAsNailsLoaded()) {
            MAX_POSITIONS_PER_BIOME = 500;
        } else {
            MAX_POSITIONS_PER_BIOME = 1000;
        }
    }

    static final int RADIUS = SimpleCloudsConstants.SPAWN_RADIUS;


    static final Set<BiomeInstanceKey> biomeSamples = ConcurrentHashMap.newKeySet();

    // Build once at startup or cache
    private static Map<ResourceLocation, List<BiomeInstanceKey>> biomeIndex = new ConcurrentHashMap<>();


    private static final Map<ResourceLocation, Integer> biomeSampleCounts = new ConcurrentHashMap<>();


    private static final Map<ResourceLocation, BiomeForecast> AVERAGE_FORECASTS = new ConcurrentHashMap<>();


    static final Map<BiomeInstanceKey, BiomeForecast> FORECAST_MAP = new ConcurrentHashMap<>();
    static final Map<RegionInstanceKey, ForecastRegion> REGION_FORECASTS = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, List<BiomeForecast>> grouped = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> WARNED_MISSING_FORECASTS = ConcurrentHashMap.newKeySet();

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
        buildRegionForecasts();
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
        // Local helper class for per-biome accumulation and size checking
        final class BiomeStats {
            int count;
            int minX;
            int maxX;
            int minZ;
            int maxZ;
            final List<BiomeInstanceKey> samples = new ArrayList<>(MAX_POSITIONS_PER_BIOME);

            BiomeStats(int x, int z) {
                this.count = 0;
                this.minX = this.maxX = x;
                this.minZ = this.maxZ = z;
            }

            void updateBounds(int x, int z) {
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }
        }

        final long start = System.nanoTime();
        // Your “too small” biome threshold (both dimensions < 40 blocks).
        final int minBiomeWidthBlocks = 40;

        // Optional but recommended: clear shared state for this region if not cleared elsewhere.
        biomeSamples.clear();
        biomeSampleCounts.clear();
        biomeIndex.clear();
        FORECAST_MAP.clear();

        // --- World-dependent values (must be on main thread) ---
        long day = AsyncAtmosphereService.callOnMainThread(
                () -> level.getDayTime() / 24000L
        );

        BiomeSource biomeSource = AsyncAtmosphereService.callOnMainThread(
                () -> level.getChunkSource().getGenerator().getBiomeSource()
        );

        // --- Sampling phase ---
        final long samplingStart = System.nanoTime();

        final int baseX = center.getX();
        final int baseY = center.getY();
        final int baseZ = center.getZ();

        final boolean belowSeaLevel = baseY < SEA_LEVEL;

        BiomeSampler sampler = new BiomeSampler(ProjectAtmosphere.seed, level.registryAccess(), biomeSource);

        // Estimate number of samples to reduce resizes.
        final int samplesPerAxis = (RADIUS * 2) / SAMPLE_STEP + 1;
        final int estimatedSamples = samplesPerAxis * samplesPerAxis;
        final Map<ResourceLocation, BiomeStats> biomeStats =
                new HashMap<>(estimatedSamples / 4 + 16);

        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        if (!belowSeaLevel) {
            for (int dx = -RADIUS; dx <= RADIUS; dx += SAMPLE_STEP) {
                final int x = baseX + dx;
                for (int dz = -RADIUS; dz <= RADIUS; dz += SAMPLE_STEP) {
                    final int z = baseZ + dz;
                    mutablePos.set(x, baseY, z);

                    // Biome lookup (hot path)
                    ResourceLocation biomeId = sampler.getBiomeId(x, baseY, z);

                    // Skip specific cave biomes
                    String path = biomeId.getPath();
                    if (path.equals("lush_caves")
                            || path.equals("dripstone_caves")
                            || path.equals("deep_dark")) {
                        continue;
                    }

                    // Accumulate per-biome stats
                    BiomeStats stats = biomeStats.get(biomeId);
                    if (stats == null) {
                        stats = new BiomeStats(x, z);
                        biomeStats.put(biomeId, stats);
                    } else {
                        stats.updateBounds(x, z);
                    }

                    // Limit number of stored positions per biome
                    if (stats.count < MAX_POSITIONS_PER_BIOME) {
                        // Store immutable position only when we actually keep this sample
                        BiomeInstanceKey key = new BiomeInstanceKey(biomeId, mutablePos.immutable());
                        stats.samples.add(key);
                        stats.count++;
                    }
                }
            }
        }
        // If below sea level, no sampling is done at all, which is equivalent to
        // your old behavior where every sample was skipped (no entries added).

        // Flatten per-biome stats into the shared structures,
        // dropping biomes that are too small (both dimensions < minBiomeWidthBlocks).
        for (Map.Entry<ResourceLocation, BiomeStats> entry : biomeStats.entrySet()) {
            ResourceLocation biomeId = entry.getKey();
            BiomeStats stats = entry.getValue();

            int widthX = stats.maxX - stats.minX;
            int widthZ = stats.maxZ - stats.minZ;

            // Discard tiny "island" biomes.
            if (widthX < minBiomeWidthBlocks && widthZ < minBiomeWidthBlocks) {
                continue;
            }

            biomeSampleCounts.put(biomeId, stats.count);
            biomeSamples.addAll(stats.samples);
        }

        // Build biome index without streams to reduce allocations.
        for (BiomeInstanceKey key : biomeSamples) {
            biomeIndex
                    .computeIfAbsent(key.biomeType(), k -> new ArrayList<>())
                    .add(key);
        }

        final long samplingEnd = System.nanoTime();

        // --- Forecast generation phase (temperature / TAN) ---
        final long forecastStart = samplingEnd;

        if (CompatHandler.isToughAsNailsLoaded()) {
            // Process each biome only once (as before) using a Set.
            Set<ResourceLocation> processed = new HashSet<>(biomeIndex.size());
            for (BiomeInstanceKey key : biomeSamples) {
                ResourceLocation biomeId = key.biomeType();
                if (!processed.add(biomeId)) {
                    continue; // already handled this biome
                }

                long perBiomeStart = System.nanoTime();

                float[][] forecast = ToughAsNailsCompat.injectForecastForTAN(key, level);

                BiomeForecast bf = new BiomeForecast();
                bf.setTemperature(forecast);
                bf.setToughAsNailsFlag(true);
                bf.setBiomeKey(key);
                putForecast(key, bf);

                if (ProjectAtmosphere.DEBUG_MODE) {
                    long perBiomeEnd = System.nanoTime();
                    long micros = (perBiomeEnd - perBiomeStart) / 1_000L;
                    ProjectAtmosphere.LOGGER.info(
                            "[Atmosphere] Tough As Nails forecast for " + biomeId +
                                    " at " + key.samplePos() +
                                    " took " + micros + " µs"
                    );
                }
            }
            groupForecastsByBiome();
        } else {
            // Per-sample temperature generation (kept for semantic equivalence).
            for (BiomeInstanceKey key : biomeSamples) {
                BiomeForecast forecast = new BiomeForecast();
                forecast.setTemperature(generateTemperature(key, level));
                forecast.setBiomeKey(key);
                putForecast(key, forecast);
            }
            groupForecastsByBiome();
        }

        // Compute temperature averages as before.
        computeAverageTemperatureWeek();

        final long forecastEnd = System.nanoTime();

        // --- Post-processing: humidity, pressure, wind ---
        final long postStart = forecastEnd;

        // Single pass over FORECAST_MAP to set all three fields.
// 1) Humidity first
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            BiomeForecast forecast = entry.getValue();

            forecast.setHumidity(generateHumidity(key, level, day));
        }
        computeAverageHumidityWeek(); // now any global humidity info is valid

// 2) Then pressure
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            BiomeForecast forecast = entry.getValue();

            forecast.setPressure(generatePressure(key, day));
        }
        computeAveragePressureWeek(); // pressure averages now valid too
        WindGenerator.buildNeighborIndex(getBiomeSamples());

// 3) Finally wind, with access to humidity (+ averages) and pressure
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            BiomeForecast forecast = entry.getValue();

            // generateWind can now safely read:
            // - forecast.getHumidity()
            // - forecast.getPressure()
            // - any global humidity/pressure averages computed above
            forecast.setWind(generateWind(key));
        }
        computeAverageWindWeek();


        // NOTE: if dailyAndSand touches world state directly, it may need
        // AsyncAtmosphereService.callOnMainThread as well.
        buildRegionForecasts();
        SandStormManager.dailyAndSand(level);

        final long end = System.nanoTime();

        long totalMs = (end - start) / 1_000_000L;

        if (ProjectAtmosphere.DEBUG_MODE) {
            long samplingMs = (samplingEnd - samplingStart) / 1_000_000L;
            long forecastMs = (forecastEnd - forecastStart) / 1_000_000L;
            long postMs = (end - postStart) / 1_000_000L;
            String message = "[Atmosphere] Forecast region generation took " + totalMs + " ms. " +
                    "sampling=" + samplingMs + " ms, forecast=" + forecastMs + " ms, post=" + postMs + " ms. " +
                    "samples=" + biomeSamples.size() + ", biomes=" + biomeIndex.size();

            ProjectAtmosphere.LOGGER.info(message);
            ForecastGenerator.message = message;
        } else {
            ProjectAtmosphere.LOGGER.info(
                    "[Atmosphere] Forecast region generation took " + totalMs +
                            " ms for " + biomeSamples.size() + " samples across " + biomeIndex.size() + " biomes."
            );
        }
    }


    private static float[][] generateTemperature(BiomeInstanceKey key, ServerLevel level) {
        float[][] base = TemperatureGenerator.generateWeekForecast(level, key.samplePos(), key.biomeType());
        var clamp = TemperatureGenerator.getSeasonClamp(level, key.biomeType());
        return VariationGenerator.applyVariationToWeek(base, clamp);
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

    private static void buildRegionForecasts() {
        REGION_FORECASTS.clear();
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeInstanceKey biomeKey = entry.getKey();
            if (biomeKey == null || biomeKey.samplePos() == null) {
                continue;
            }
            RegionInstanceKey regionKey = RegionInstanceKey.from(biomeKey.samplePos());
            ForecastRegion region = REGION_FORECASTS.computeIfAbsent(regionKey, key -> new ForecastRegion(key, biomeKey.samplePos()));
            region.addBiomeForecast(biomeKey, entry.getValue());
        }
        for (ForecastRegion region : REGION_FORECASTS.values()) {
            region.finalizeAggregation();
            AtmosphericStateRegistry.initializeState(region.getKey(), region);
        }
        AtmosphericStateRegistry.rebuildNeighbors();
    }


    public static Map<BiomeInstanceKey, BiomeForecast> getForecastMap() {
        return FORECAST_MAP;
    }

    public static Map<RegionInstanceKey, ForecastRegion> getRegionForecasts() {
        return REGION_FORECASTS;
    }

    static void clearForecasts() {
        FORECAST_MAP.clear();
        REGION_FORECASTS.clear();
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
        BiomeForecast pointer = ForecastPointerRegistry.getPointer(key);
        if (pointer != null) {
            return pointer;
        }

        BiomeForecast average = getAverageForecast(key.biomeType());
        if (average != null) {
            return average;
        }

        if (WARNED_MISSING_FORECASTS.add(key.biomeType())) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] No forecast data available for {}. Returning fallback.", key);
        }
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

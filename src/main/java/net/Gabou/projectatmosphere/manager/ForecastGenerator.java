package net.Gabou.projectatmosphere.manager;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.async.BiomeSampler;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.ToughAsNailsCompat;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingStage;
import net.Gabou.projectatmosphere.client.loading.IntegratedForecastLoadingBridge;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.BiomeForecastSnapshot;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.humidity.HumidityGenerator;
import net.Gabou.projectatmosphere.modules.pressure.PressureGenerator;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator;
import net.Gabou.projectatmosphere.modules.temperature.variation.VariationGenerator;
import net.Gabou.projectatmosphere.modules.wind.WindGenerator;
import net.Gabou.projectatmosphere.network.BiomeDayTemperaturePacket;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraftforge.network.PacketDistributor;
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
        rebuildAverageForecasts();
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new BiomeDayTemperaturePacket(createDailyTemperatureSnapshot()));
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
        grouped.clear();
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            ResourceLocation biomeType = entry.getKey().biomeType();
            grouped.computeIfAbsent(biomeType, k -> new ArrayList<>()).add(entry.getValue());
        }
    }


    public static BiomeForecast getAverageForecast(ResourceLocation biomeType) {
        return AVERAGE_FORECASTS.get(biomeType);
    }

    public static void sendDailyForecastsToPlayer(ServerPlayer player, Map<ResourceLocation, float[]> snapshot) {
        if (player == null) {
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BiomeDayTemperaturePacket(snapshot));
    }

    public static Map<ResourceLocation, float[]> createDailyTemperatureSnapshotForSync() {
        return createDailyTemperatureSnapshot();
    }

    public static Set<BiomeInstanceKey> getBiomeSamples() {
        return biomeSamples;
    }


    static void clearBiomeSamples() {
        biomeSamples.clear();
        biomeIndex.clear();
    }


    static void generateForecastForSavedRegion(ServerLevel level) {
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                "Restoring saved forecast regions",
                0.34F,
                "generate_saved_region_restore"
        );
        buildRegionForecasts();
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                "Applying sandstorm state",
                0.82F,
                "generate_saved_region_sand"
        );
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
        grouped.clear();
        AVERAGE_FORECASTS.clear();

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
        int samplingColumns = samplesPerAxis;
        int samplingIndex = 0;

        if (!belowSeaLevel) {
            for (int dx = -RADIUS; dx <= RADIUS; dx += SAMPLE_STEP) {
                samplingIndex++;
                if (samplingIndex == 1 || samplingIndex == samplingColumns || samplingIndex % 4 == 0) {
                    float samplingProgress = 0.16F + (0.22F * samplingIndex / Math.max(1.0F, (float) samplingColumns));
                    IntegratedForecastLoadingBridge.update(
                            ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                            "Sampling biome layout " + samplingIndex + " / " + samplingColumns,
                            samplingProgress,
                            "generate_region_sampling"
                    );
                }
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
        int biomeSampleTotal = Math.max(1, biomeSamples.size());

        if (CompatHandler.isToughAsNailsLoaded()) {
            // Process each biome only once (as before) using a Set.
            Set<ResourceLocation> processed = new HashSet<>(biomeIndex.size());
            int totalBiomes = Math.max(1, biomeIndex.size());
            int processedCount = 0;
            for (BiomeInstanceKey key : biomeSamples) {
                ResourceLocation biomeId = key.biomeType();
                if (!processed.add(biomeId)) {
                    continue; // already handled this biome
                }
                processedCount++;
                if (processedCount == 1 || processedCount == totalBiomes || processedCount % 4 == 0) {
                    float forecastProgress = 0.42F + (0.18F * processedCount / Math.max(1.0F, (float) totalBiomes));
                    IntegratedForecastLoadingBridge.update(
                            ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                            "Generating temperature forecast " + processedCount + " / " + totalBiomes,
                            forecastProgress,
                            "generate_region_temperature_tan"
                    );
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
            int processedCount = 0;
            for (BiomeInstanceKey key : biomeSamples) {
                processedCount++;
                if (processedCount == 1 || processedCount == biomeSampleTotal || processedCount % 32 == 0) {
                    float forecastProgress = 0.42F + (0.18F * processedCount / (float) biomeSampleTotal);
                    IntegratedForecastLoadingBridge.update(
                            ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                            "Generating temperature forecast " + processedCount + " / " + biomeSampleTotal,
                            forecastProgress,
                            "generate_region_temperature"
                    );
                }
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
        computeDependentForecasts(level, day);


        // NOTE: if dailyAndSand touches world state directly, it may need
        // AsyncAtmosphereService.callOnMainThread as well.
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                "Building forecast regions",
                0.84F,
                "generate_region_build_regions"
        );
        buildRegionForecasts();
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                "Applying sandstorm state",
                0.9F,
                "generate_region_sand"
        );
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

    private static void computeDependentForecasts(ServerLevel level, long day) {
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                "Computing humidity forecast",
                0.66F,
                "generate_region_humidity"
        );
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            BiomeForecast forecast = entry.getValue();
            forecast.setHumidity(generateHumidity(key, level, day));
        }
        computeAverageHumidityWeek();

        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                "Computing pressure forecast",
                0.72F,
                "generate_region_pressure"
        );
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            BiomeForecast forecast = entry.getValue();
            forecast.setPressure(generatePressure(key, day));
        }
        computeAveragePressureWeek();

        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                "Computing wind forecast",
                0.78F,
                "generate_region_wind"
        );
        WindGenerator.buildNeighborIndex(getBiomeSamples());
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            BiomeForecast forecast = entry.getValue();
            forecast.setWind(generateWind(key));
        }
        computeAverageWindWeek();
        computeAverageForecastsByBiomeType();
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

    public static void putRegionForecast(ForecastRegion region) {
        if (region == null || region.getKey() == null) {
            return;
        }
        region.finalizeAggregation();
        REGION_FORECASTS.put(region.getKey(), region);
        AtmosphericStateRegistry.initializeState(region.getKey(), region);
        hydrateLegacyBiomeForecasts(region);
    }

    public static void rebuildLoadedForecastIndexes() {
        groupForecastsByBiome();
        groupBiomeByType();
        AVERAGE_FORECASTS.clear();
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
        BiomeForecast direct = FORECAST_MAP.get(key);
        if (direct != null) {
            return direct;
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

    private static void hydrateLegacyBiomeForecasts(ForecastRegion region) {
        List<BiomeInstanceKey> sources = region.sourceBiomes();
        if (sources != null && !sources.isEmpty()) {
            for (BiomeInstanceKey key : sources) {
                if (key == null || key.samplePos() == null || key.biomeType() == null) {
                    continue;
                }
                putForecast(key, buildLegacyForecastFromRegion(region, key));
            }
            return;
        }

        for (ForecastRegion.Section section : region.sections()) {
            BiomeForecastSnapshot snapshot = section.snapshot();
            if (snapshot == null || snapshot.biomeKey() == null) {
                continue;
            }
            putForecast(snapshot.biomeKey(), buildLegacyForecastFromRegion(region, snapshot.biomeKey()));
        }
    }

    private static BiomeForecast buildLegacyForecastFromRegion(ForecastRegion region, BiomeInstanceKey key) {
        BiomeForecast forecast = new BiomeForecast();
        forecast.setBiomeKey(key);
        forecast.setTemperature(copyWeek(region.getTemperature()));
        forecast.setHumidity(copyWeek(region.getHumidity()));
        forecast.setPressure(copyWeek(region.getPressure()));
        forecast.setWind(copyWindWeek(region.getWind()));
        if (forecast.getWind() != null && forecast.getWind().length > 0) {
            forecast.setWindDay(forecast.getWind()[0]);
        }
        return forecast;
    }

    private static float[][] copyWeek(float[][] week) {
        if (week == null) {
            return null;
        }
        float[][] copy = new float[week.length][];
        for (int i = 0; i < week.length; i++) {
            copy[i] = week[i] == null ? null : Arrays.copyOf(week[i], week[i].length);
        }
        return copy;
    }

    private static WindVector[] copyWindWeek(WindVector[] week) {
        return week == null ? null : Arrays.copyOf(week, week.length);
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

    private static void rebuildAverageForecasts() {
        AVERAGE_FORECASTS.clear();
        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) {
                continue;
            }

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), key -> new BiomeForecast());
            avg.setTemperature(averageWeek(list, BiomeForecast::getTemperature));
            avg.setHumidity(averageWeek(list, BiomeForecast::getHumidity));
            avg.setPressure(averageWeek(list, BiomeForecast::getPressure));
            avg.setWind(averageWindWeek(list, BiomeForecast::getWind));
            avg.setBiomeKey(list.get(0).getBiomeKey());
        }
    }

    private static Map<ResourceLocation, float[]> createDailyTemperatureSnapshot() {
        if (AVERAGE_FORECASTS.isEmpty() && !grouped.isEmpty()) {
            rebuildAverageForecasts();
        }

        Map<ResourceLocation, float[]> map = new HashMap<>(AVERAGE_FORECASTS.size());
        for (Map.Entry<ResourceLocation, BiomeForecast> entry : AVERAGE_FORECASTS.entrySet()) {
            map.put(entry.getKey(), buildFlatCurve(deriveRepresentativeTemperature(entry.getValue())));
        }
        return map;
    }

}

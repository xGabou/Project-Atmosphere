package net.Gabou.projectatmosphere.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.async.BiomeSampler;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingStage;
import net.Gabou.projectatmosphere.client.loading.IntegratedForecastLoadingBridge;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.ToughAsNailsCompat;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.humidity.HumidityGenerator;
import net.Gabou.projectatmosphere.modules.pressure.PressureGenerator;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.RegionBiomeSample;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator;
import net.Gabou.projectatmosphere.modules.temperature.variation.VariationGenerator;
import net.Gabou.projectatmosphere.modules.wind.WindGenerator;
import net.Gabou.projectatmosphere.network.BiomeDayTemperaturePacket;
import net.Gabou.projectatmosphere.platform.network.AtmosphereNetwork;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.BiomeSource;

public class ForecastGenerator {
    private static final int SAMPLE_STEP = 64;
    static long seed = 0L;
    static final boolean sandStormLoaded = CompatHandler.isSandStormsLoaded();
    public static final int MAX_POSITIONS_PER_BIOME = CompatHandler.isToughAsNailsLoaded() ? 500 : 1000;
    static final float SEA_LEVEL = AsyncAtmosphereService.callOnMainThread(ProjectAtmosphere::getSeaLevel);
    static final int RADIUS = Math.min(ProjectAtmosphere.DEFAULT_RADIUS / 5, 10000);

    public static String message = "";

    static final Map<RegionInstanceKey, ForecastRegion> REGION_FORECASTS = new ConcurrentHashMap<>();

    public static Map<RegionInstanceKey, ForecastRegion> getRegionForecasts() {
        return REGION_FORECASTS;
    }

    static void generateForecastForSavedRegion(ServerLevel level) {
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                "Restoring saved forecast regions",
                0.34F,
                "generate_saved_region_restore"
        );
        for (ForecastRegion region : REGION_FORECASTS.values()) {
            AtmosphericStateRegistry.initializeState(region.getKey(), region);
        }
        AtmosphericStateRegistry.rebuildNeighbors();
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                "Applying sandstorm state",
                0.82F,
                "generate_saved_region_sand"
        );
        SandStormManager.dailyAndSand(level);
    }

    static void generateForecastForRegion(BlockPos center, ServerLevel level) {
        final long start = System.nanoTime();
        clearTransientGenerationState();

        Map<RegionInstanceKey, List<RegionBiomeSample>> samplesByRegion = sampleAround(center, level, RADIUS);
        int total = Math.max(1, samplesByRegion.size());
        int index = 0;
        for (Map.Entry<RegionInstanceKey, List<RegionBiomeSample>> entry : samplesByRegion.entrySet()) {
            index++;
            IntegratedForecastLoadingBridge.update(
                    ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                    "Generating region forecast " + index + " / " + total,
                    0.42F + (0.36F * index / Math.max(1.0F, (float) total)),
                    "generate_region_direct"
            );
            ForecastRegion region = generateRegion(entry.getKey(), centerFor(entry.getKey(), level), entry.getValue(), level);
            putRegionForecast(region);
        }

        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                "Applying sandstorm state",
                0.9F,
                "generate_region_sand"
        );
        SandStormManager.dailyAndSand(level);
        computeAverageForecastsByBiomeType();

        long totalMs = (System.nanoTime() - start) / 1_000_000L;
        message = "[Atmosphere] Forecast region generation took " + totalMs + " ms for "
                + REGION_FORECASTS.size() + " region(s).";
        ProjectAtmosphere.LOGGER.info(message);
    }

    public static ForecastRegion generateForecastForRegionKey(RegionInstanceKey id, ServerLevel level) {
        BlockPos center = centerFor(id, level);
        List<RegionBiomeSample> samples = sampleRegionCell(id, level);
        ForecastRegion region = generateRegion(id, center, samples, level);
        putRegionForecast(region);
        return region;
    }

    public static void putRegionForecast(ForecastRegion region) {
        if (region == null || region.getKey() == null) {
            return;
        }
        region.finalizeAggregation();
        REGION_FORECASTS.put(region.getKey(), region);
        AtmosphericStateRegistry.initializeState(region.getKey(), region);
        AtmosphericStateRegistry.rebuildNeighbors();
    }

    public static void rebuildLoadedForecastIndexes() {
        for (ForecastRegion region : REGION_FORECASTS.values()) {
            AtmosphericStateRegistry.initializeState(region.getKey(), region);
        }
        AtmosphericStateRegistry.rebuildNeighbors();
    }

    static void clearForecasts() {
        REGION_FORECASTS.clear();
        AtmosphericStateRegistry.clear();
        SandStormManager.clearSandstormForecasts();
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Cleared all region forecasts.");
    }

    public static void sendDailyForecastsToPlayer(ServerPlayer player, Map<ResourceLocation, float[]> snapshot) {
        if (player == null) {
            return;
        }
        AtmosphereNetwork.sendToPlayer(player, new BiomeDayTemperaturePacket(snapshot));
    }

    public static Map<ResourceLocation, float[]> createDailyTemperatureSnapshotForSync() {
        return createDailyTemperatureSnapshot();
    }

    static void computeAverageForecastsByBiomeType() {
        AtmosphereNetwork.sendToAll(new BiomeDayTemperaturePacket(createDailyTemperatureSnapshot()));
    }

    public static float[][] getRegionTemperatureWeek(ServerLevel level, BlockPos pos) {
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        return region == null ? null : region.getTemperature();
    }

    private static ForecastRegion generateRegion(RegionInstanceKey id,
                                                 BlockPos anchor,
                                                 List<RegionBiomeSample> samples,
                                                 ServerLevel level) {
        long day = AsyncAtmosphereService.callOnMainThread(() -> level.getDayTime() / 24000L);
        if (samples == null || samples.isEmpty()) {
            return new ForecastRegion(id, anchor);
        }

        List<ForecastRegion.GeneratedSample> partial = new ArrayList<>(samples.size());
        int total = Math.max(1, samples.size());
        int processed = 0;
        for (RegionBiomeSample sample : samples) {
            processed++;
            if (processed == 1 || processed == total || processed % 32 == 0) {
                IntegratedForecastLoadingBridge.update(
                        ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                        "Generating sample curves " + processed + " / " + total,
                        0.48F + (0.22F * processed / Math.max(1.0F, (float) total)),
                        "generate_region_sample_curves"
                );
            }
            float[][] temperature = generateTemperature(sample, level);
            float[][] humidity = HumidityGenerator.generateWeekForecast(level, sample, temperature, day);
            float[][] pressure = PressureGenerator.generateWeekForecast(sample, temperature, humidity, day);
            partial.add(new ForecastRegion.GeneratedSample(sample, temperature, humidity, pressure, null));
        }
        List<ForecastRegion.GeneratedSample> generated = WindGenerator.attachWindForecasts(partial);
        return ForecastRegion.aggregate(id, anchor, generated, new float[0]);
    }

    private static float[][] generateTemperature(RegionBiomeSample sample, ServerLevel level) {
        if (CompatHandler.isToughAsNailsLoaded()) {
            return ToughAsNailsCompat.injectForecastForTAN(level, sample.pos());
        }
        float[][] base = TemperatureGenerator.generateWeekForecast(level, sample.pos(), sample.biomeId());
        var clamp = TemperatureGenerator.getSeasonClamp(level, sample.biomeId());
        return VariationGenerator.applyVariationToWeek(base, clamp);
    }

    private static Map<RegionInstanceKey, List<RegionBiomeSample>> sampleAround(BlockPos center, ServerLevel level, int radius) {
        Map<RegionInstanceKey, List<RegionBiomeSample>> samplesByRegion = new LinkedHashMap<>();
        for (RegionBiomeSample sample : sampleSquare(center, level, radius)) {
            samplesByRegion.computeIfAbsent(RegionInstanceKey.from(sample.pos()), ignored -> new ArrayList<>()).add(sample);
        }
        return samplesByRegion;
    }

    private static List<RegionBiomeSample> sampleRegionCell(RegionInstanceKey id, ServerLevel level) {
        BlockPos center = centerFor(id, level);
        int half = Math.max(SAMPLE_STEP, id.regionSize() / 2);
        List<RegionBiomeSample> samples = new ArrayList<>();
        for (RegionBiomeSample sample : sampleSquare(center, level, half)) {
            if (id.contains(sample.pos())) {
                samples.add(sample);
            }
        }
        return samples;
    }

    private static List<RegionBiomeSample> sampleSquare(BlockPos center, ServerLevel level, int radius) {
        if (center.getY() < SEA_LEVEL) {
            return List.of();
        }

        BiomeSource biomeSource = AsyncAtmosphereService.callOnMainThread(
                () -> level.getChunkSource().getGenerator().getBiomeSource()
        );
        BiomeSampler sampler = new BiomeSampler(ProjectAtmosphere.seed, level.registryAccess(), biomeSource);
        Map<ResourceLocation, BiomeStats> statsByBiome = new HashMap<>();
        int samplesPerAxis = (radius * 2) / SAMPLE_STEP + 1;
        int sampleColumn = 0;

        for (int dx = -radius; dx <= radius; dx += SAMPLE_STEP) {
            sampleColumn++;
            if (sampleColumn == 1 || sampleColumn == samplesPerAxis || sampleColumn % 4 == 0) {
                IntegratedForecastLoadingBridge.update(
                        ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                        "Sampling biome layout " + sampleColumn + " / " + samplesPerAxis,
                        0.16F + (0.22F * sampleColumn / Math.max(1.0F, (float) samplesPerAxis)),
                        "generate_region_sampling"
                );
            }
            int x = center.getX() + dx;
            for (int dz = -radius; dz <= radius; dz += SAMPLE_STEP) {
                int z = center.getZ() + dz;
                ResourceLocation biomeId = sampler.getBiomeId(x, center.getY(), z);
                if (isSkippedBiome(biomeId)) {
                    continue;
                }
                BiomeStats stats = statsByBiome.computeIfAbsent(biomeId, ignored -> new BiomeStats(x, z));
                stats.add(x, z, new RegionBiomeSample(biomeId, new BlockPos(x, center.getY(), z), 1));
            }
        }

        List<RegionBiomeSample> samples = new ArrayList<>();
        for (BiomeStats stats : statsByBiome.values()) {
            if (!stats.isTiny()) {
                samples.addAll(stats.samples);
            }
        }
        return samples;
    }

    private static boolean isSkippedBiome(ResourceLocation biomeId) {
        String path = biomeId.getPath();
        return path.equals("lush_caves") || path.equals("dripstone_caves") || path.equals("deep_dark");
    }

    private static BlockPos centerFor(RegionInstanceKey id, ServerLevel level) {
        BlockPos center = id.center();
        return new BlockPos(center.getX(), level.getSeaLevel(), center.getZ());
    }

    private static void clearTransientGenerationState() {
        // Region forecasts are intentionally retained; callers decide when a full
        // clear is needed. This method exists to document the absence of biome maps.
    }

    private static Map<ResourceLocation, float[]> createDailyTemperatureSnapshot() {
        Map<ResourceLocation, WeightedTemperature> grouped = new HashMap<>();
        for (ForecastRegion region : REGION_FORECASTS.values()) {
            float representative = averageDailyMidpoint(region.getTemperature());
            Map<ResourceLocation, Integer> weights = region.getBiomeWeights();
            if (weights.isEmpty()) {
                ResourceLocation dominant = region.getDominantBiome();
                if (dominant != null) {
                    grouped.computeIfAbsent(dominant, ignored -> new WeightedTemperature()).add(representative, 1);
                }
                continue;
            }
            for (Map.Entry<ResourceLocation, Integer> entry : weights.entrySet()) {
                grouped.computeIfAbsent(entry.getKey(), ignored -> new WeightedTemperature())
                        .add(representative, Math.max(1, entry.getValue()));
            }
        }

        Map<ResourceLocation, float[]> snapshot = new HashMap<>();
        for (Map.Entry<ResourceLocation, WeightedTemperature> entry : grouped.entrySet()) {
            snapshot.put(entry.getKey(), buildFlatCurve(entry.getValue().average()));
        }
        return snapshot;
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
            if (day == null || day.length == 0) {
                continue;
            }
            sum += day.length == 1 ? day[0] : (day[0] + day[1]) * 0.5f;
            count++;
        }
        return count == 0 ? 0f : sum / count;
    }

    private static final class WeightedTemperature {
        private float sum;
        private int weight;

        void add(float value, int amount) {
            sum += value * amount;
            weight += amount;
        }

        float average() {
            return weight <= 0 ? 0f : sum / weight;
        }
    }

    private static final class BiomeStats {
        private static final int MIN_BIOME_WIDTH_BLOCKS = 40;
        private int minX;
        private int maxX;
        private int minZ;
        private int maxZ;
        private final List<RegionBiomeSample> samples = new ArrayList<>(MAX_POSITIONS_PER_BIOME);

        BiomeStats(int x, int z) {
            minX = maxX = x;
            minZ = maxZ = z;
        }

        void add(int x, int z, RegionBiomeSample sample) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            if (samples.size() < MAX_POSITIONS_PER_BIOME) {
                samples.add(sample);
            }
        }

        boolean isTiny() {
            return (maxX - minX) < MIN_BIOME_WIDTH_BLOCKS && (maxZ - minZ) < MIN_BIOME_WIDTH_BLOCKS;
        }
    }
}

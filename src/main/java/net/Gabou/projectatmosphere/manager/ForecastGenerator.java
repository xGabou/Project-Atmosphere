package net.Gabou.projectatmosphere.manager;

import com.BreadRes.desertstormwarming.logic.SandstormPhase;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.LegendarySurvivalCompat;
import net.Gabou.projectatmosphere.compat.ToughAsNailsCompat;
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
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ForecastGenerator {

    private static final int DIFFUSION_RADIUS = 200; // blocks
    private static final float DIFFUSION_RATE = 0.1f;
    private static final int SAMPLE_STEP = 256;
    private static BiomeInstanceKey scheduledStormBiome = null;
    private static SandstormPhase scheduledStormPhase = null;
    private static long scheduledStormTime = -1L; // in world ticks

    public static BiomeInstanceKey getScheduledSandstormBiome() {
        return scheduledStormBiome;
    }


    private static final float SANDSTORM_WIND_THRESHOLD = 20f;  // Example, adjust
    private static final float SANDSTORM_HUMIDITY_THRESHOLD = 40f;
    private static final float SANDSTORM_PRESSURE_THRESHOLD = 1000f;


    public static final int MAX_POSITIONS_PER_BIOME;

    private static final Set<ResourceLocation> SANDSTORM_BIOMES = Set.of(
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
        if (CompatHandler.isLegendaryModLoaded || CompatHandler.isToughAsNailsLoaded) {
            MAX_POSITIONS_PER_BIOME = 10;
        } else {
            MAX_POSITIONS_PER_BIOME = 40;
        }
    }

    static final int RADIUS = SimpleCloudsConstants.SPAWN_RADIUS;


    static final Set<BiomeInstanceKey> biomeSamples = ConcurrentHashMap.newKeySet();

    private static final Map<ResourceLocation, Integer> biomeSampleCounts = new ConcurrentHashMap<>();


    private static final Map<ResourceLocation, BiomeForecast> AVERAGE_FORECASTS = new ConcurrentHashMap<>();


    static final Map<BiomeInstanceKey, BiomeForecast> FORECAST_MAP = new ConcurrentHashMap<>();

    public static void computeAverageForecastsByBiomeType() {
        computeAverageTemperature();
        computeAverageHumidity();
        computeAveragePressure();
        computeAverageWind();
        computeAverageStormChance();
    }

    public static void computeAverageTemperature() {
        Map<ResourceLocation, List<BiomeForecast>> grouped = groupForecastsByBiome();

        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setTemperature(averageWeek(list, BiomeForecast::getTemperature));
            avg.setTemperatureDay(averageDay(list, BiomeForecast::getTemperatureDay));
            avg.setTemperatureTomorrow(averageDay(list, BiomeForecast::getTemperatureTomorrow));
        }
    }

    private static Map<ResourceLocation, List<BiomeForecast>> groupForecastsByBiome() {
        Map<ResourceLocation, List<BiomeForecast>> grouped = new HashMap<>();

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            ResourceLocation biomeType = entry.getKey().biomeType();
            grouped.computeIfAbsent(biomeType, k -> new ArrayList<>()).add(entry.getValue());
        }

        return grouped;
    }

    public static void computeAverageStormChance() {
        Map<ResourceLocation, List<BiomeForecast>> grouped = groupForecastsByBiome();

        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setStormChance(averageWeek(list, BiomeForecast::getStormChance));
            avg.setStormChanceDay(averageDay(list, BiomeForecast::getStormChanceDay));
            avg.setStormChanceTomorrow(averageDay(list, BiomeForecast::getStormChanceTomorrow));
        }
    }

    public static void computeAverageWind() {
        Map<ResourceLocation, List<BiomeForecast>> grouped = groupForecastsByBiome();

        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setWind(averageWindWeek(list, BiomeForecast::getWind));
            avg.setWindDay(averageWind(list, BiomeForecast::getWindDay));
            avg.setWindTomorrow(averageWind(list, BiomeForecast::getWindTomorrow));
        }
    }

    public static void computeAveragePressure() {
        Map<ResourceLocation, List<BiomeForecast>> grouped = groupForecastsByBiome();

        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setPressure(averageWeek(list, BiomeForecast::getPressure));
            avg.setPressureDay(averageDay(list, BiomeForecast::getPressureDay));
            avg.setPressureTomorrow(averageDay(list, BiomeForecast::getPressureTomorrow));
        }
    }

    public static void computeAverageHumidity() {
        Map<ResourceLocation, List<BiomeForecast>> grouped = groupForecastsByBiome();

        for (Map.Entry<ResourceLocation, List<BiomeForecast>> entry : grouped.entrySet()) {
            List<BiomeForecast> list = entry.getValue();
            if (list.isEmpty()) continue;

            BiomeForecast avg = AVERAGE_FORECASTS.computeIfAbsent(entry.getKey(), k -> new BiomeForecast());

            avg.setHumidity(averageWeek(list, BiomeForecast::getHumidity));
            avg.setHumidityDay(averageDay(list, BiomeForecast::getHumidityDay));
            avg.setHumidityTomorrow(averageDay(list, BiomeForecast::getHumidityTomorrow));
        }
    }


    private static boolean shouldTriggerSandstorm(BiomeInstanceKey key, float[][] humidity, float[][] pressure, WindVector wind) {
        if (!SANDSTORM_BIOMES.contains(key.biomeType())) return false;

        float todayHumidityMin = humidity[0][0]; // today = index 0, min = [0]
        float todayPressureMin = pressure[0][0]; // same
        float windSpeed = wind != null ? wind.speed() : 0;

        boolean dryEnough = todayHumidityMin < SANDSTORM_HUMIDITY_THRESHOLD;
        boolean windyEnough = windSpeed > SANDSTORM_WIND_THRESHOLD;
        boolean unstablePressure = todayPressureMin < SANDSTORM_PRESSURE_THRESHOLD;

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
    }


    public static void generateForecastForSavedRegion(ServerLevel level) {
        dailyAndSand(level);
    }

    private static void dailyAndSand(ServerLevel level) {
        clearSandstormForecasts();

        FORECAST_MAP.entrySet().stream()
                .filter(entry -> SANDSTORM_BIOMES.contains(entry.getKey().biomeType()))
                .filter(entry -> {
                    BiomeForecast f = entry.getValue();
                    return f.getHumidity() != null && f.getPressure() != null && f.getWind() != null;
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


        // 10. Génération courbes journalières
        DailyForecastGenerator.scheduleAll(level, FORECAST_MAP);
        // Recompute averages now that daily profiles are available
        computeAverageForecastsByBiomeType();

        AsyncAtmosphereService.runStorm(() -> {
                    if (!SandStormAPI.isSandstormActive() && scheduledStormBiome == null && !SANDSTORM_FORECASTS.isEmpty()) {
                        BiomeInstanceKey selected = SANDSTORM_FORECASTS.stream()
                                .skip(level.random.nextInt(SANDSTORM_FORECASTS.size()))
                                .findFirst()
                                .orElse(null);

                        if (selected != null) {
                            BiomeForecast forecast = FORECAST_MAP.get(selected);
                            if (forecast != null) {
                                long baseTime = (level.getDayTime() / 24000L) * 24000L;
                                long randomOffset = 1000 + level.random.nextInt(9000); // Between 0.5–5 min into the day

                                scheduledStormBiome = selected;
                                scheduledStormPhase = computeStormPhase(forecast);
                                scheduledStormTime = baseTime + randomOffset;

                                ProjectAtmosphere.LOGGER.info("[Atmosphere] Scheduled sandstorm at tick {} in biome {} (phase: {})",
                                        scheduledStormTime, selected.biomeType(), scheduledStormPhase);
                            }
                        }
                    }
                }
        );
    }

    /**
     * Generates a weekly forecast for the specified region centered at the given position.
     * This method samples biomes in a square area around the center and generates forecasts
     * based on the sampled biomes.
     *
     * @param center The center position of the region to sample.
     * @param level  The server level where the region is located.
     */
    public static void generateForecastForRegion(BlockPos center, ServerLevel level) {
        long start = System.nanoTime(); // Start timer

        for (int dx = -RADIUS; dx <= RADIUS; dx += SAMPLE_STEP) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += SAMPLE_STEP) {
                BlockPos samplePos = center.offset(dx, 0, dz);

                level.getBiome(samplePos).unwrapKey().ifPresent(biomeKey -> {
                    ResourceLocation biomeId = biomeKey.location();

                    // Cap at 10 samples per biome
                    int count = biomeSampleCounts.getOrDefault(biomeId, 0);
                    if (count >= MAX_POSITIONS_PER_BIOME) return;

                    BiomeInstanceKey key = new BiomeInstanceKey(biomeId, samplePos);
                    if (biomeSamples.add(key)) {
                        biomeSampleCounts.put(biomeId, count + 1);
                    }
                });
            }
        }


        if (CompatHandler.isLegendaryModLoaded) {
            // Forecast using Legendary Survival Overhaul temperature
            Map<ResourceLocation, Integer> biomeSampleCount = new HashMap<>();

            for (BiomeInstanceKey key : biomeSamples) {
                ResourceLocation biomeId = key.biomeType(); // your BiomeInstanceKey should expose this

                // Limit to 2 samples per biome type
                int count = biomeSampleCount.getOrDefault(biomeId, 0);
                if (count >= 1)
                    continue;

                biomeSampleCount.put(biomeId, count + 1);

                long sampleTime = System.currentTimeMillis();
                float[][] forecast = LegendarySurvivalCompat.injectForecastForLSO(key, level);
                long endTime = System.currentTimeMillis();
                ProjectAtmosphere.LOGGER.info("[Atmosphere] Legendary Survival forecast for " + key.biomeType() + " at " + key.samplePos() + " took " + (endTime - sampleTime) + " ms");
                BiomeForecast bf = new BiomeForecast();
                bf.setTemperature(forecast);
                bf.setLegendaryFlag(true);

                FORECAST_MAP.put(key, bf);
            }


        } else if (CompatHandler.isToughAsNailsLoaded) {
            // Forecast using Tough As Nails temperature
            Map<ResourceLocation, Integer> biomeSampleCount = new HashMap<>();
            for (BiomeInstanceKey key : biomeSamples) {
                ResourceLocation biomeId = key.biomeType(); // your BiomeInstanceKey should expose this

                // Limit to 2 samples per biome type
                int count = biomeSampleCount.getOrDefault(biomeId, 0);
                if (count >= 1)
                    continue;

                long sampleTime = System.currentTimeMillis();
                biomeSampleCount.put(biomeId, count + 1);
                float[][] forecast = ToughAsNailsCompat.injectForecastForTAN(key, level);
                BiomeForecast bf = new BiomeForecast();
                long endTime = System.currentTimeMillis();
                ProjectAtmosphere.LOGGER.info("[Atmosphere] Tough as Nail forecast for " + key.biomeType() + " at " + key.samplePos() + " took " + (endTime - sampleTime) + " ms");
                bf.setTemperature(forecast);
                bf.setToughAsNailsFlag(true);
                FORECAST_MAP.put(key, bf);
            }

        } else {
            // 1. Température brute
            for (BiomeInstanceKey key : biomeSamples) {
                BiomeForecast forecast = new BiomeForecast();
                forecast.setTemperature(generateTemperature(key, level));  // your internal logic
                FORECAST_MAP.put(key, forecast);
            }

            // 2. Diffusion température
            diffuseAndSmoothField(BiomeForecast::getTemperature, BiomeForecast::setTemperature);
        }

        computeAverageTemperature();
        // 3. Humidité (après température)
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setHumidity(generateHumidity(entry.getKey(), level));
        }

        // 4. Diffusion humidité
        diffuseAndSmoothField(BiomeForecast::getHumidity, BiomeForecast::setHumidity);

        computeAverageHumidity();

        // 5. Pression (après temp + humidité)
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setPressure(generatePressure(entry.getKey(), level));
        }

        // 6. Diffusion pression
        diffuseAndSmoothField(BiomeForecast::getPressure, BiomeForecast::setPressure);

        computeAveragePressure();
        // 7. Vent (dépend de tout)
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setWind(generateWind(entry.getKey(), level));
        }

        computeAverageWind();
        // 8. Génération des tempêtes
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            entry.getValue().setStormChance(generateStorm(entry.getKey(), level, entry.getValue().getTemperature(), entry.getValue().getHumidity(), entry.getValue().getPressure(), entry.getValue().getWind()));
        }
        computeAverageStormChance();
        // 9. Check for sandstorm conditions
        dailyAndSand(level);


        long end = System.nanoTime(); // End timer
        long durationMs = (end - start) / 1_000_000;
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Forecast region generation took " + durationMs + " ms.");
    }

    public static void tickSandstormScheduler(ServerLevel level) {
        if (scheduledStormBiome != null && level.getDayTime() >= scheduledStormTime) {
            SandStormAPI.startSandstorm(scheduledStormPhase);

            ProjectAtmosphere.LOGGER.info("[Atmosphere] Triggered sandstorm in biome {} with phase {}",
                    scheduledStormBiome.biomeType(), scheduledStormPhase);

            scheduledStormBiome = null;
            scheduledStormTime = -1L;
            scheduledStormPhase = null;
        }
    }

    private static float[][] generateStorm(BiomeInstanceKey key, ServerLevel level, float[][] temperature, float[][] humidity, float[][] pressure, WindVector[] wind) {
        return StormGenerator.generateWeeklyStormProfile(key, level, temperature, humidity, pressure, wind);
    }


    private static float[][] generateTemperature(BiomeInstanceKey key, ServerLevel level) {
        return SpikeManager.applySpikeLogic(key,
                VariationGenerator.applyVariationToWeek(
                        TemperatureGenerator.generateWeekForecast(level, key.samplePos(), key.biomeType())
                ));
    }

    private static float[][] generateHumidity(BiomeInstanceKey key, ServerLevel level) {
        return HumidityGenerator.generateWeekForecast(level, key);
    }

    private static float[][] generatePressure(BiomeInstanceKey key, ServerLevel level) {
        return PressureGenerator.generateWeekForecast(level, key);
    }

    private static WindVector[] generateWind(BiomeInstanceKey key, ServerLevel level) {
        return WindGenerator.generateWindWeek(key);
    }


    public static Map<BiomeInstanceKey, BiomeForecast> getForecastMap() {
        return FORECAST_MAP;
    }

    private static void diffuseAndSmoothField(Function<BiomeForecast, float[][]> getter,
                                              BiConsumer<BiomeForecast, float[][]> setter) {
        long threshold = DIFFUSION_RADIUS * DIFFUSION_RADIUS;

        Map<BiomeInstanceKey, float[][]> original = new HashMap<>();
        for (var entry : FORECAST_MAP.entrySet()) {
            float[][] data = getter.apply(entry.getValue());
            if (data != null) {
                original.put(entry.getKey(), data);
            }
        }

        Map<BiomeInstanceKey, float[][]> diffused = new HashMap<>();

        for (var entry : original.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            float[][] week = entry.getValue();
            BlockPos pos = key.samplePos();

            Map<BiomeInstanceKey, float[][]> neighbors = new HashMap<>();
            for (var other : original.entrySet()) {
                if (!other.getKey().equals(key) && other.getKey().samplePos().distSqr(pos) <= threshold) {
                    neighbors.put(other.getKey(), other.getValue());
                }
            }

            if (neighbors.isEmpty()) {
                diffused.put(key, week);
                continue;
            }

            float[][] smoothed = new float[7][2];
            for (int d = 0; d < 7; d++) {
                for (int i = 0; i < 2; i++) {
                    float val = week[d][i];
                    float sum = 0, count = 0;
                    for (float[][] n : neighbors.values()) {
                        sum += n[d][i];
                        count++;
                    }
                    float avg = sum / count;
                    smoothed[d][i] = val + DIFFUSION_RATE * (avg - val);
                }
            }

            diffused.put(key, smoothed);
        }

        // Apply 3-day smoothing on diffused result
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

    public static void clearForecasts() {
        FORECAST_MAP.clear();
    }

    public static void putForecast(BiomeInstanceKey key, BiomeForecast forecast) {
        FORECAST_MAP.put(key, forecast);
    }

    public static BiomeForecast getForecast(BiomeInstanceKey key) {
        return FORECAST_MAP.get(key);
    }


    public static float getHumidityValue(BiomeInstanceKey key, long tick) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.HUMIDITY);
        if (forecast == null) return 0.0f;

        float[] curve = forecast.getHumidityDay();
        int minuteOfDay = (int) ((tick % 24000L) / 100L);
        return curve[Math.min(minuteOfDay, curve.length - 1)];
    }

    public static float getTemperatureValue(BiomeInstanceKey key, long tick) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.TEMPERATURE);
        if (forecast == null) return 0.0f;

        float[] curve = forecast.getTemperatureDay();
        int minuteOfDay = (int) ((tick % 24000L) / 100L);
        return curve[Math.min(minuteOfDay, curve.length - 1)];
    }

    public static float getStormChanceValue(BiomeInstanceKey key, long tick) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.STORM);
        if (forecast == null) return 0.0f;
        float[] curve = forecast.getStormChanceDay();
        int minuteOfDay = (int) ((tick % 24000L) / 100L);
        return curve[Math.min(minuteOfDay, curve.length - 1)];
    }

    public static float getPressureValue(BiomeInstanceKey key, long tick) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.PRESSURE);
        if (forecast == null) return 0.0f;

        float[] curve = forecast.getPressureDay();
        int minuteOfDay = (int) ((tick % 24000L) / 100L);
        return curve[Math.min(minuteOfDay, curve.length - 1)];
    }

    public static WindVector getWindValue(BiomeInstanceKey key) {
        BiomeForecast forecast = getClosestValidForecast(key, ForecastType.WIND);
        if (forecast == null) return new WindVector(0, 0);

        WindVector wind = forecast.getWindDay();
        return wind != null ? wind : new WindVector(0, 0);
    }


    public static BiomeForecast getClosestValidForecast(BiomeInstanceKey key, ForecastType type) {
        BiomeForecast direct = FORECAST_MAP.get(key);
        if (direct != null && direct.hasData(type)) {
            return direct;
        }

        // 2. Try to use the average forecast for this biome type
        BiomeForecast avg = AVERAGE_FORECASTS.get(key.biomeType());
        if (avg != null && avg.hasData(type)) {
            return avg;
        }

        BiomeForecast closestSame = null;
        double minDistSame = Double.MAX_VALUE;

        // 1. Try to find same biome type (by biomeId) with available data
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeInstanceKey otherKey = entry.getKey();
            BiomeForecast forecast = entry.getValue();

            if (!forecast.hasData(type)) continue;
            if (!otherKey.biomeType().equals(key.biomeType())) continue;

            double dist = otherKey.samplePos().distSqr(key.samplePos());
            if (dist < minDistSame) {
                minDistSame = dist;
                closestSame = forecast;
                if (dist < SAMPLE_STEP * 2) break; // early exit for near-perfect match
            }
        }

        if (closestSame != null) return closestSame;


        // 3. Fallback to any closest biome with valid data
        BiomeForecast closestFallback = null;
        double minDistAny = Double.MAX_VALUE;

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeForecast forecast = entry.getValue();
            if (!forecast.hasData(type)) continue;

            double dist = entry.getKey().samplePos().distSqr(key.samplePos());
            if (dist < minDistAny) {
                minDistAny = dist;
                closestFallback = forecast;
            }
        }

        return closestFallback;
    }


    public static void swapToTomorrow() {
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : FORECAST_MAP.entrySet()) {
            BiomeForecast forecast = entry.getValue();

            // 1. Rotate raw 7-day data forward
            forecast.setTemperature(rotateWeek(forecast.getTemperature()));
            forecast.setHumidity(rotateWeek(forecast.getHumidity()));
            forecast.setPressure(rotateWeek(forecast.getPressure()));
            forecast.setStormChance(rotateWeek(forecast.getStormChance()));
            forecast.setWind(rotateWindWeek(forecast.getWind()));

            // 2. Shift daily interpolations
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
    }

    private static float[][] rotateWeek(float[][] original) {
        if (original == null || original.length < 2) return original;

        int len = original.length;
        float[][] rotated = new float[len][2];

        for (int i = 0; i < len - 1; i++) {
            rotated[i] = original[i + 1];
        }

        // Optionally reset the last day
        rotated[len - 1] = new float[]{0f, 0f}; // or reuse previous, or regenerate on demand
        return rotated;
    }

    private static WindVector[] rotateWindWeek(WindVector[] original) {
        if (original == null || original.length < 2) return original;

        int len = original.length;
        WindVector[] rotated = new WindVector[len];

        for (int i = 0; i < len - 1; i++) {
            rotated[i] = original[i + 1];
        }

        rotated[len - 1] = new WindVector(0, 0); // or null if you want to regenerate
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
        if (forecasts.isEmpty()) return new WindVector(0, 0);

        float sumX = 0;
        float sumZ = 0;

        for (BiomeForecast f : forecasts) {
            WindVector wind = f.getWindDay();
            if (wind == null) continue;

            float angle = wind.angleRadians();
            float speed = wind.speed();

            sumX += speed * (float) Math.cos(angle);
            sumZ += speed * (float) Math.sin(angle);
        }

        int size = forecasts.size();
        float avgX = sumX / size;
        float avgZ = sumZ / size;

        float avgSpeed = (float) Math.sqrt(avgX * avgX + avgZ * avgZ);
        float avgAngle = (float) Math.atan2(avgZ, avgX);

        return new WindVector(avgSpeed, avgAngle);
    }

    private static WindVector[] averageWindWeek(List<BiomeForecast> forecasts, Function<BiomeForecast, WindVector[]> extractor) {
        WindVector[] result = new WindVector[7];
        if (forecasts.isEmpty()) {
            Arrays.fill(result, new WindVector(0, 0));
            return result;
        }

        for (int day = 0; day < 7; day++) {
            float sumX = 0f;
            float sumZ = 0f;
            int count = 0;

            for (BiomeForecast forecast : forecasts) {
                WindVector[] windWeek = forecast.getWind();
                if (windWeek == null || windWeek.length != 7) continue;

                WindVector wind = windWeek[day];
                if (wind == null) continue;

                float speed = wind.speed();
                float angle = wind.angleRadians();

                sumX += (float) (speed * Math.cos(angle));
                sumZ += (float) (speed * Math.sin(angle));
                count++;
            }

            if (count > 0) {
                float avgX = sumX / count;
                float avgZ = sumZ / count;
                float avgSpeed = (float) Math.sqrt(avgX * avgX + avgZ * avgZ);
                float avgAngle = (float) Math.atan2(avgZ, avgX);
                result[day] = new WindVector(avgSpeed, avgAngle);
            } else {
                result[day] = new WindVector(0, 0); // fallback
            }
        }

        return result;
    }

    private static SandstormPhase computeStormPhase(BiomeForecast forecast) {
        float wind = forecast.getWind()[0].speed();
        float pressure = forecast.getPressure()[0][0];
        float humidity = forecast.getHumidity()[0][0];

        if (wind > 35 && pressure < 980 && humidity < 0.15f) return SandstormPhase.PHASE_5;
        if (wind > 30) return SandstormPhase.PHASE_4;
        if (wind > 25) return SandstormPhase.PHASE_3;
        if (wind > 20) return SandstormPhase.PHASE_2;
        return SandstormPhase.PHASE_1;
    }

}

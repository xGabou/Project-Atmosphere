package net.Gabou.projectatmosphere.modules.temperature.util;

import javax.annotation.Nullable;

import net.Gabou.projectatmosphere.api.Celsius;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.RegionAdapters;
import net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig;
import net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Season;
import net.Gabou.projectatmosphere.seasons.SeasonSnapshot;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves a fast local biome temperature from an existing region forecast.
 * The resolver blends regional air mass temperature with biome climate ranges,
 * then applies a lightweight altitude correction at query time.
 */
public final class LocalBiomeTemperatureResolver {
    private static final long TIME_BUCKET_TICKS = 3000L;
    private static final double SEA_LEVEL_LAPSE_RATE = 0.0065D;
    private static final double PRESSURE_REFERENCE_HPA = 1013.25D;
    private static final double FREEZING_SNOW_THRESHOLD_C = 0.35D;
    private static final double MIN_LOCAL_TEMPERATURE_C = -90.0D;
    private static final double MAX_LOCAL_TEMPERATURE_C = 70.0D;
    private static final ResourceLocation FALLBACK_BIOME_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");

    private static final ConcurrentMap<CacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ResourceLocation, Long> LAST_CLEANED_BUCKET = new ConcurrentHashMap<>();

    private LocalBiomeTemperatureResolver() {
    }

    /**
     * Returns the local biome temperature for a world position.
     *
     * <p>The cached value stores the biome-local base temperature only. The fast
     * altitude correction is applied after cache lookup so the same bucket can be
     * reused across different Y values inside the same region.</p>
     *
     * @param level the server level being sampled
     * @param pos the world position to evaluate
     * @param regionKey the enclosing region key
     * @param forecast the existing region forecast; if null, the current forecast is resolved on demand
     * @return the local biome temperature in degrees Celsius
     */
    public static double getLocalBiomeTemperature(ServerLevel level,
                                                  BlockPos pos,
                                                  RegionInstanceKey regionKey,
                                                  @Nullable ForecastRegion forecast) {
        if (level == null || pos == null || regionKey == null) {
            return 0.0D;
        }

        long timeBucket = getTimeBucket(level.getDayTime());
        cleanupIfNeeded(level, timeBucket);

        ForecastRegion resolvedForecast = resolveForecast(level, pos, forecast);
        if (resolvedForecast == null) {
            return getFastAltitudeCorrection(level, pos);
        }

        ResourceLocation biomeId = resolveBiomeId(level, pos);
        Season season = resolveSeason(level);
        CacheKey cacheKey = new CacheKey(level.dimension().location(), regionKey, biomeId, season, timeBucket);
        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null) {
            return clampLocalTemperature(cached.baseTemperature() + getFastAltitudeCorrection(level, pos));
        }

        double baseTemperature = computeBaseLocalBiomeTemperature(level, pos, regionKey, resolvedForecast, biomeId, season, timeBucket);
        CACHE.put(cacheKey, new CacheEntry(baseTemperature));
        return clampLocalTemperature(baseTemperature + getFastAltitudeCorrection(level, pos));
    }

    /**
     * Removes all cached entries that belong to older time buckets for the given level.
     *
     * @param level the server level whose cache entries should be compacted
     */
    public static void cleanup(ServerLevel level) {
        if (level == null) {
            return;
        }
        ResourceLocation dimensionId = level.dimension().location();
        long currentBucket = getTimeBucket(level.getDayTime());
        CACHE.keySet().removeIf(key -> dimensionId.equals(key.dimensionId()) && key.timeBucket() != currentBucket);
    }

    /**
     * Determines whether snow can accumulate at the target position.
     *
     * <p>This method is intended for block-freezing/snowfall checks and reuses the
     * same cached local temperature path so repeated calls stay close to O(1).</p>
     *
     * @param level the server level being sampled
     * @param pos the world position to evaluate
     * @param regionKey the enclosing region key
     * @param forecast the existing region forecast; if null, the current forecast is resolved on demand
     * @return when the local biome is cold and wet enough for snow accumulation
     */
    public static Celsius canAccumulateSnow(ServerLevel level,
                                            BlockPos pos,
                                            RegionInstanceKey regionKey,
                                            @Nullable ForecastRegion forecast) {
        ForecastRegion resolvedForecast = resolveForecast(level, pos, forecast);
        if (resolvedForecast == null) {
            return new Celsius(false, -1);
        }

//        long sampleTick = getBucketSampleTick(getTimeBucket(level.getDayTime()));
//        Vec3 localPos = RegionAdapters.toRegionLocal(pos, regionKey);
//        float humidity = resolvedForecast.sampleHumidity(localPos, sampleTick);
//        float storm = resolvedForecast.sampleStorm(sampleTick);
        double localTemperature = getLocalBiomeTemperature(level, pos, regionKey, resolvedForecast);

        return new Celsius(localTemperature <= FREEZING_SNOW_THRESHOLD_C, localTemperature);
                /*&& humidity >= 50.0D
                && storm >= 0.30D*/
    }

    /**
     * Converts an absolute world tick into the cache bucket used by this resolver.
     *
     * @param gameTime the current world time
     * @return the coarse time bucket used for caching
     */
    public static long getTimeBucket(long gameTime) {
        return Math.floorDiv(gameTime, TIME_BUCKET_TICKS);
    }

    /**
     * Computes the uncached local biome temperature before the fast altitude correction is added.
     *
     * <p>This blends the region forecast with the biome's seasonal range, then applies
     * humidity, pressure, and wind adjustments using the bucket's representative tick.</p>
     *
     * @param level the server level being sampled
     * @param pos the world position to evaluate
     * @param regionKey the enclosing region key
     * @param forecast the resolved region forecast
     * @param biomeId the biome id at the position, or null if unavailable
     * @param season the current season
     * @param timeBucket the coarse cache bucket
     * @return the cached-base local biome temperature in degrees Celsius
     */
    private static double computeBaseLocalBiomeTemperature(ServerLevel level,
                                                           BlockPos pos,
                                                           RegionInstanceKey regionKey,
                                                           ForecastRegion forecast,
                                                           @Nullable ResourceLocation biomeId,
                                                           BiomeTempConfig.Season season,
                                                           long timeBucket) {
        long sampleTick = getBucketSampleTick(timeBucket);
        Vec3 localPos = RegionAdapters.toRegionLocal(pos, regionKey);

        float regionTemperature = forecast.sampleTemperature(localPos, sampleTick);
        float biomeExpectedTemperature = interpolateDailyTemperature(getSeasonalClamp(biomeId, season), sampleTick);
        double biomeWeight = computeBiomeWeight(getSeasonalRange(biomeId, season), regionTemperature, biomeExpectedTemperature);

        double blendedBase = Mth.lerp((float) biomeWeight, regionTemperature, biomeExpectedTemperature);
        boolean precipitating = isPrecipitating(forecast, sampleTick);

        double humidityCooling = computeHumidityCooling(forecast.sampleHumidity(localPos, sampleTick), blendedBase, precipitating);
        double pressureEffect = computePressureEffect(forecast.samplePressure(sampleTick), precipitating);
        double windCooling = computeWindCooling(forecast.sampleWind(sampleTick).baseSpeed(), blendedBase, precipitating);

        return clampLocalTemperature(blendedBase + humidityCooling + pressureEffect + windCooling);
    }

    /**
     * Interpolates the biome's expected daily temperature from its seasonal clamp.
     *
     * <p>The curve is intentionally simple: it anchors the coldest period near midnight,
     * warms through the morning, peaks in the afternoon, and cools again during the evening.</p>
     *
     * @param clamp the biome's daily temperature clamp for the season
     * @param gameTime the current world time
     * @return the interpolated daily temperature in degrees Celsius
     */
    private static float interpolateDailyTemperature(@Nullable BiomeTempConfig.DailyRange clamp, long gameTime) {
        if (clamp == null) {
            return 0.0F;
        }

        long timeOfDay = Math.floorMod(gameTime, 24000L);
        float dayFraction = timeOfDay / 24000.0F;

        if (dayFraction < 0.25F) {
            return Mth.lerp(dayFraction / 0.25F, clamp.minMin(), clamp.avgNight());
        }
        if (dayFraction < 0.50F) {
            return Mth.lerp((dayFraction - 0.25F) / 0.25F, clamp.avgNight(), clamp.avgDay());
        }
        if (dayFraction < 0.75F) {
            return Mth.lerp((dayFraction - 0.50F) / 0.25F, clamp.avgDay(), clamp.maxMax());
        }
        return Mth.lerp((dayFraction - 0.75F) / 0.25F, clamp.maxMax(), clamp.minMin());
    }

    /**
     * Computes how strongly the biome should pull the local temperature toward its own climate range.
     *
     * <p>Colder and narrower biomes exert more influence, while warmer or broader
     * biomes bias less strongly toward the biome-specific value.</p>
     *
     * @param seasonalRange the biome's seasonal min/max range
     * @param regionTemperature the regional air-mass temperature
     * @param biomeExpectedTemperature the biome's interpolated daily target temperature
     * @return a blend factor in the range {@code [0, 1]}
     */
    private static double computeBiomeWeight(@Nullable BiomeTempConfig.Range seasonalRange,
                                             double regionTemperature,
                                             double biomeExpectedTemperature) {
        if (seasonalRange == null) {
            return 0.0D;
        }

        double span = Math.max(0.001D, seasonalRange.maxC() - seasonalRange.minC());
        double center = (seasonalRange.minC() + seasonalRange.maxC()) * 0.5D;
        double spanFactor = 1.0D - Mth.clamp(span / 40.0D, 0.0D, 1.0D);
        double coldFactor = Mth.clamp((10.0D - center) / 30.0D, 0.0D, 1.0D);
        double deltaFactor = Mth.clamp(Math.abs(regionTemperature - biomeExpectedTemperature) / 18.0D, 0.0D, 1.0D);

        return Mth.clamp(0.18D + (spanFactor * 0.30D) + (coldFactor * 0.24D) + (deltaFactor * 0.28D), 0.10D, 0.85D);
    }

    /**
     * Computes a small humidity-based cooling adjustment.
     *
     * <p>Higher humidity increases cloud cover and reduces daytime heating. When the
     * forecast is precipitating, the cooling contribution becomes stronger.</p>
     *
     * @param humidityPercent the forecast humidity in percent
     * @param baseTemperature the current local base temperature before humidity is applied
     * @param precipitating whether the forecast is currently precipitating
     * @return the humidity-driven temperature delta in degrees Celsius
     */
    private static double computeHumidityCooling(double humidityPercent,
                                                 double baseTemperature,
                                                 boolean precipitating) {
        double humidityFactor = Mth.clamp(humidityPercent / 100.0D, 0.0D, 1.2D);
        double temperatureFactor = Mth.clamp((baseTemperature + 10.0D) / 35.0D, 0.0D, 1.0D);
        double precipBoost = precipitating ? 1.35D : 1.0D;

        return -(0.18D + temperatureFactor * 0.55D) * humidityFactor * precipBoost;
    }

    /**
     * Computes a very small pressure-driven temperature adjustment.
     *
     * <p>Lower pressure slightly cools the local biome and higher pressure slightly warms it,
     * with a modest boost during precipitating conditions.</p>
     *
     * @param pressureHpa the forecast pressure in hPa
     * @param precipitating whether the forecast is currently precipitating
     * @return the pressure-driven temperature delta in degrees Celsius
     */
    private static double computePressureEffect(double pressureHpa, boolean precipitating) {
        double normalized = Mth.clamp((PRESSURE_REFERENCE_HPA - pressureHpa) / 45.0D, -1.5D, 1.5D);
        double effect = -normalized * 0.26D;
        if (precipitating) {
            effect -= Math.copySign(Math.min(Math.abs(normalized) * 0.08D, 0.20D), normalized);
        }
        return effect;
    }

    /**
     * Computes wind chill from the forecast wind speed.
     *
     * <p>Wind cooling is stronger when the local base temperature is already cold
     * or when precipitation is active.</p>
     *
     * @param windSpeed the forecast wind speed in blocks per second or meters per second
     * @param baseTemperature the current local base temperature before wind is applied
     * @param precipitating whether the forecast is currently precipitating
     * @return the wind-driven temperature delta in degrees Celsius
     */
    private static double computeWindCooling(double windSpeed,
                                             double baseTemperature,
                                             boolean precipitating) {
        double speedFactor = Mth.clamp(Math.max(0.0D, windSpeed) / 18.0D, 0.0D, 1.0D);
        double coldBias = Mth.clamp((8.0D - baseTemperature) / 16.0D, 0.0D, 1.0D);
        double precipBoost = precipitating ? 1.25D : 1.0D;

        return -speedFactor * (0.20D + coldBias * 0.85D) * precipBoost * 2.2D;
    }

    /**
     * Computes the fast altitude correction applied after the cached base value is read.
     *
     * <p>This is intentionally linear and cheap: every block above sea level cools the
     * local temperature by a small lapse-rate factor.</p>
     *
     * @param level the server level being sampled
     * @param pos the world position to evaluate
     * @return the altitude correction in degrees Celsius
     */
    private static double getFastAltitudeCorrection(ServerLevel level, BlockPos pos) {
        double heightDelta = level.getSeaLevel() - pos.getY();
        return Mth.clamp(heightDelta * SEA_LEVEL_LAPSE_RATE, -14.0D, 14.0D);
    }

    /**
     * Resolves the biome id at the target position.
     *
     * @param level the server level being sampled
     * @param pos the world position to evaluate
     * @return the biome id, or null if the lookup fails
     */
    @Nullable
    private static ResourceLocation resolveBiomeId(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(key -> key.location()).orElse(null);
    }

    /**
     * Maps the active season provider state into the biome temperature season enum.
     *
     * @param level the server level being sampled
     * @return the current biome-season bucket
     */
    private static BiomeTempConfig.Season resolveSeason(ServerLevel level) {
        SeasonSnapshot snapshot = SeasonTimeHelper.snapshot(level);
        SeasonStage stage = snapshot.stage();
        return switch (stage) {
            case WINTER -> BiomeTempConfig.Season.WINTER;
            case SPRING -> BiomeTempConfig.Season.SPRING;
            case AUTUMN -> BiomeTempConfig.Season.AUTUMN;
            default -> BiomeTempConfig.Season.SUMMER;
        };
    }

    /**
     * Resolves the region forecast to use for the temperature calculation.
     *
     * @param level the server level being sampled
     * @param pos the world position to evaluate
     * @param forecast the caller-provided forecast, if any
     * @return the forecast to use, or null if no forecast could be resolved
     */
    @Nullable
    private static ForecastRegion resolveForecast(ServerLevel level, BlockPos pos, @Nullable ForecastRegion forecast) {
        if (forecast != null) {
            return forecast;
        }
        return ForecastOrchestrator.getRegionForecast(level, pos);
    }

    /**
     * Returns the representative tick used for a cached time bucket.
     *
     * <p>Sampling the bucket midpoint keeps the cached result stable across all calls
     * that land inside the same 3000-tick window.</p>
     *
     * @param timeBucket the current world time
     * @return the midpoint tick used to sample the forecast
     */
    private static long getBucketSampleTick(long timeBucket) {
        return (timeBucket * TIME_BUCKET_TICKS) + (TIME_BUCKET_TICKS / 2L);
    }

    /**
     * Resolves the seasonal min/max range for a biome.
     *
     * @param biomeId the biome id at the target position
     * @param season the current biome season
     * @return the seasonal range, or null when no biome id is available
     */
    @Nullable
    private static BiomeTempConfig.Range getSeasonalRange(@Nullable ResourceLocation biomeId, BiomeTempConfig.Season season) {
        if (biomeId == null) {
            return null;
        }
        BiomeTempConfig.Range range = BiomeTempConfig.getRange(biomeId, season);
        return range != null ? range : BiomeTempConfig.getRange(FALLBACK_BIOME_ID, season);
    }

    /**
     * Resolves the seasonal daily clamp for a biome.
     *
     * @param biomeId the biome id at the target position
     * @param season the current biome season
     * @return the seasonal daily clamp, or null when no biome id is available
     */
    @Nullable
    private static BiomeTempConfig.DailyRange getSeasonalClamp(@Nullable ResourceLocation biomeId, BiomeTempConfig.Season season) {
        if (biomeId == null) {
            return null;
        }
        BiomeTempConfig.DailyRange clamp = BiomeTempConfig.getClamp(biomeId, season);
        if (clamp != null) {
            return clamp;
        }
        BiomeTempConfig.Range fallbackRange = BiomeTempConfig.getRange(FALLBACK_BIOME_ID, season);
        return fallbackRange == null ? null : BiomeTempConfig.deriveDaily(fallbackRange);
    }

    /**
     * Returns whether the forecast is currently precipitating.
     *
     * @param forecast the region forecast being sampled
     * @param sampleTick the representative sample tick
     * @return true when the forecast should be treated as precipitating
     */
    private static boolean isPrecipitating(ForecastRegion forecast, long sampleTick) {
        return forecast.sampleStorm(sampleTick) >= 0.35F;
    }

    /**
     * Clamps the final local temperature to a safe Celsius range.
     *
     * @param temperature the computed temperature
     * @return the clamped temperature
     */
    private static double clampLocalTemperature(double temperature) {
        return Mth.clamp(temperature, MIN_LOCAL_TEMPERATURE_C, MAX_LOCAL_TEMPERATURE_C);
    }

    /**
     * Performs opportunistic cache cleanup once per bucket transition.
     *
     * @param level the server level being sampled
     * @param currentBucket the current cache bucket
     */
    private static void cleanupIfNeeded(ServerLevel level, long currentBucket) {
        ResourceLocation dimensionId = level.dimension().location();
        Long previousBucket = LAST_CLEANED_BUCKET.put(dimensionId, currentBucket);
        if (previousBucket == null || previousBucket.longValue() != currentBucket) {
            cleanup(level);
        }
    }

    /**
     * Cache key that isolates entries by region, biome, season, and time bucket.
     *
     * @param dimensionId the level dimension id
     * @param regionKey the enclosing region key
     * @param biomeId the biome id, if one is available
     * @param season the active season
     * @param timeBucket the 3000-tick cache bucket
     */
    private record CacheKey(ResourceLocation dimensionId,
                            RegionInstanceKey regionKey,
                            @Nullable ResourceLocation biomeId,
                            BiomeTempConfig.Season season,
                            long timeBucket) {
    }

    /**
     * Stores the cached base local temperature for a key.
     *
     * @param baseTemperature the cached base temperature before altitude correction
     */
    private record CacheEntry(double baseTemperature) {
    }
}

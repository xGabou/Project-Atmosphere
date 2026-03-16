package net.Gabou.projectatmosphere.modules.region;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.telemetry.TelemetryCollector;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.ChannelSummary;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.DayCurve;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.ForecastSnapshot;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.Modifiers;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Coordinates creation, loading, and runtime access to ForecastRegion instances.
 */
public final class RegionForecastOrchestrator {
    private static final float CORRUPTION_TEMP_MIN = -90f;
    private static final float CORRUPTION_TEMP_MAX = 70f;
    private static final float CORRUPTION_HUMIDITY_MIN = 0f;
    private static final float CORRUPTION_HUMIDITY_MAX = 100f;
    private static final float CORRUPTION_PRESSURE_MIN = 880f;
    private static final float CORRUPTION_PRESSURE_MAX = 1085f;
    private final RegionIndex regionIndex;
    private final RegionPersistence persistence;
    private final BiomeForecastGenerator biomeGenerator;
    private final Map<RegionInstanceKey, ForecastRegion> regions = new ConcurrentHashMap<>();

    public RegionForecastOrchestrator(RegionIndex regionIndex,
                                      RegionPersistence persistence,
                                      BiomeForecastGenerator biomeGenerator) {
        this.regionIndex = regionIndex;
        this.persistence = persistence;
        this.biomeGenerator = biomeGenerator;
    }

    public ForecastRegion resolve(BlockPos pos, ResourceKey<Level> dimension) {
        RegionInstanceKey id = regionIndex.regionFor(pos, dimension);
        return ensureLoaded(id);
    }

    public ForecastRegion ensureLoaded(RegionInstanceKey id) {
        return regions.computeIfAbsent(id, this::loadOrGenerate);
    }

    private ForecastRegion loadOrGenerate(RegionInstanceKey id) {
        Optional<ForecastRegion> persisted = persistence.loadRegion(id);
        if (persisted.isPresent()) {
            ForecastRegion region = persisted.get();
            CorruptionReport report = detectCorruption(region);
            if (report.corrupted()) {
                ProjectAtmosphere.LOGGER.warn(
                        "[Atmosphere] Region {} primary save appears corrupted ({}). Falling back to migration path.",
                        id,
                        report.message()
                );
            } else {
                return region;
            }
        }
        Optional<BiomeFallbackSnapshot> fb = persistence.loadFallback(id);
        if (fb.isPresent()) {
            ForecastRegion region = fromFallback(fb.get());
            CorruptionReport report = detectCorruption(region);
            if (report.corrupted()) {
                ProjectAtmosphere.LOGGER.warn(
                        "[Atmosphere] Region {} fallback appears corrupted ({}). Regenerating from biome forecasts.",
                        id,
                        report.message()
                );
                return generateFromBiomes(id);
            }
            persistence.saveRegion(region);
            return region;
        }
        return generateFromBiomes(id);
    }

    private ForecastRegion fromFallback(BiomeFallbackSnapshot fb) {
        ForecastRegion.Section[] sections = fb.toSections();
        RegionCurves curves = aggregateSections(sections);
        ForecastRegion region = new ForecastRegion(fb.id(), fb.sourceBiomes(), sections, curves, fb);
        region.clearBiomeForecasts();
        return region;
    }

    private ForecastRegion generateFromBiomes(RegionInstanceKey id) {
        List<BiomeInstanceKey> biomes = regionIndex.biomesFor(id);
        ForecastRegion.Section[] sections = sliceIntoEight(biomes);
        RegionCurves curves = aggregateSections(sections);
        BiomeFallbackSnapshot fb = new BiomeFallbackSnapshot(id, biomes, sections);
        ForecastRegion region = new ForecastRegion(id, biomes, sections, curves, fb);
        region.clearBiomeForecasts();
        persistence.saveRegion(region);
        return region;
    }

    public void tick(long gameTime) {
        // Hook for per-tick advancement (diffusion, cloud state, gusts) keyed by region id.
    }

    private RegionCurves aggregateSections(ForecastRegion.Section[] sections) {
        WeightedCurve temperature = WeightedCurve.empty();
        WeightedCurve humidity = WeightedCurve.empty();
        WeightedCurve pressure = WeightedCurve.empty();
        WeightedCurve storm = WeightedCurve.empty();
        WeightedWindCurve wind = WeightedWindCurve.empty();
        for (ForecastRegion.Section section : sections) {
            BiomeForecastSnapshot snapshot = section.snapshot();
            if (snapshot == null) {
                continue;
            }
            temperature.add(section.factor(), snapshot.temperatureCurve());
            humidity.add(section.factor(), snapshot.humidityCurve());
            pressure.add(section.factor(), snapshot.pressureCurve());
            // Storm curve currently absent in BiomeForecast; keep zeroed via WeightedCurve.
            wind.add(section.factor(), snapshot.windCurve());
        }
        float[][] tempWeek = temperature.normalize();
        float[][] humidityWeek = humidity.normalize();
        float[][] pressureWeek = pressure.normalize();
        WindVector[] windWeek = wind.normalize();
        float[] stormWeek = flattenTwoColumn(storm.normalize());
        return new DefaultRegionCurves(tempWeek, humidityWeek, pressureWeek, windWeek, stormWeek);
    }

    private ForecastRegion.Section[] sliceIntoEight(List<BiomeInstanceKey> biomes) {
        ForecastRegion.Section[] out = new ForecastRegion.Section[8];
        for (int i = 0; i < out.length; i++) {
            BiomeForecastSnapshot snap = biomeGenerator.generateSlice(biomes, i);
            float factor = Math.max(0f, biomeGenerator.factorForSlice(biomes, i));
            if (AtmoCommonConfig.TELEMETRY_ENABLED.get() && snap != null) {
                recordForecastSnapshot(snap, i);
            }
            out[i] = new ForecastRegion.Section(factor, snap);
        }
        return out;
    }

    private static float[] flattenTwoColumn(float[][] curve) {
        if (curve == null || curve.length == 0) {
            return new float[0];
        }
        float[] result = new float[curve.length];
        for (int i = 0; i < curve.length; i++) {
            float[] row = curve[i];
            if (row == null || row.length == 0) {
                result[i] = 0f;
            } else {
                result[i] = row[0];
            }
        }
        return result;
    }

    private static void recordForecastSnapshot(BiomeForecastSnapshot snap, int dayIndex) {
        ChannelSummary temp = summarizeCurve(snap.temperatureCurve());
        ChannelSummary humidity = summarizeCurve(snap.humidityCurve());
        ChannelSummary pressure = summarizeCurve(snap.pressureCurve());
        DayCurve curve = deriveDayCurve(snap.temperatureCurve());
        ForecastSnapshot snapshot = new ForecastSnapshot(
                snap.biomeKey().biomeType().toString(),
                snap.biomeKey().samplePos() == null ? 0 : snap.biomeKey().samplePos().getX() >> 4,
                snap.biomeKey().samplePos() == null ? 0 : snap.biomeKey().samplePos().getZ() >> 4,
                dayIndex,
                temp,
                humidity,
                pressure,
                new ChannelSummary(0f, 0f),
                curve,
                new Modifiers(false, null, null, false, null)
        );
        TelemetryCollector.get().recordForecastSnapshot(snapshot);
    }

    private static CorruptionReport detectCorruption(ForecastRegion region) {
        if (region == null) {
            return new CorruptionReport(false, "");
        }
        boolean tempCorrupt = usesOnlyExtremes(region.getTemperature(), CORRUPTION_TEMP_MIN, CORRUPTION_TEMP_MAX);
        boolean humidityCorrupt = usesOnlyExtremes(region.getHumidity(), CORRUPTION_HUMIDITY_MIN, CORRUPTION_HUMIDITY_MAX);
        boolean pressureCorrupt = usesOnlyExtremes(region.getPressure(), CORRUPTION_PRESSURE_MIN, CORRUPTION_PRESSURE_MAX);

        if (!tempCorrupt && !humidityCorrupt && !pressureCorrupt) {
            return new CorruptionReport(false, "");
        }
        StringBuilder message = new StringBuilder();
        if (tempCorrupt) {
            message.append("temperature");
        }
        if (humidityCorrupt) {
            if (message.length() > 0) {
                message.append(", ");
            }
            message.append("humidity");
        }
        if (pressureCorrupt) {
            if (message.length() > 0) {
                message.append(", ");
            }
            message.append("pressure");
        }
        return new CorruptionReport(true, message.toString());
    }

    private static boolean usesOnlyExtremes(float[][] curve, float minValue, float maxValue) {
        if (curve == null || curve.length == 0) {
            return false;
        }
        boolean sawValue = false;
        for (float[] row : curve) {
            if (row == null) {
                continue;
            }
            for (float value : row) {
                if (!Float.isFinite(value)) {
                    continue;
                }
                sawValue = true;
                if (value != minValue && value != maxValue) {
                    return false;
                }
            }
        }
        return sawValue;
    }

    private record CorruptionReport(boolean corrupted, String message) {
    }

    private static ChannelSummary summarizeCurve(float[][] curve) {
        if (curve == null || curve.length == 0) {
            return new ChannelSummary(0f, 0f);
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float[] row : curve) {
            if (row == null) {
                continue;
            }
            for (float v : row) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        if (min == Float.MAX_VALUE) {
            return new ChannelSummary(0f, 0f);
        }
        return new ChannelSummary(min, max);
    }

    private static DayCurve deriveDayCurve(float[][] curve) {
        if (curve == null || curve.length == 0 || curve[0] == null || curve[0].length == 0) {
            return new DayCurve(null, null, null, null);
        }
        float[] day = curve[0];
        Float morning = day.length > 0 ? day[0] : null;
        Float afternoon = day.length > 1 ? day[1] : null;
        return new DayCurve(morning, afternoon, afternoon, morning);
    }

    public Vec3 toRegionLocal(BlockPos pos) {
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        return RegionAdapters.toRegionLocal(pos, regionKey);
    }
}

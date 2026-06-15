package net.Gabou.projectatmosphere.telemetry.verification;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.atmosphere.SeasonalAtmosphericDrift;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.FileRegionPersistence;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.RegionForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellManager;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellSavedData;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellState;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellType;
import net.Gabou.projectatmosphere.modules.wind.WindEngine;
import net.Gabou.projectatmosphere.seasons.SeasonSnapshot;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class VerificationCollector {
    private VerificationCollector() {
    }

    public static VerificationReport collect(ServerLevel level, BlockPos pos) {
        long gameTime = level.getGameTime();
        long worldTime = level.getDayTime();
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        List<String> issues = new ArrayList<>();

        VerificationReport.ForecastSection forecast = collectForecast(level, pos, regionKey, gameTime, issues);
        VerificationReport.AtmosphereSection atmosphere = collectAtmosphere(level, pos, regionKey, gameTime, forecast, issues);
        VerificationReport.WindSection wind = collectWind(level, regionKey, gameTime, issues);
        VerificationReport.SeasonSection season = collectSeason(level, issues);
        VerificationReport.WeatherCellSection weatherCells = collectWeatherCells(level, pos, issues);
        VerificationReport.CloudSection clouds = collectClouds(level, pos, issues);
        VerificationReport.MorphologySection morphology = collectMorphology(level, clouds.activeRegionCount(), issues);
        VerificationReport.EvolutionSection evolution = collectEvolution(level, pos, issues);
        VerificationReport.PersistenceSection persistence = collectPersistence(level, issues);

        return new VerificationReport(
                gameTime,
                worldTime,
                forecast,
                atmosphere,
                wind,
                season,
                weatherCells,
                clouds,
                morphology,
                evolution,
                persistence,
                issues
        );
    }

    private static VerificationReport.ForecastSection collectForecast(
            ServerLevel level,
            BlockPos pos,
            RegionInstanceKey regionKey,
            long gameTime,
            List<String> issues
    ) {
        int regionCount = ForecastGenerator.getRegionForecasts().size();
        Set<RegionInstanceKey> loadedRegions = ForecastOrchestrator.getActiveRegions(level);
        int loadedRegionCount = loadedRegions.size();
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        boolean missingRegion = region == null;
        boolean missingForecast = missingRegion && !ForecastGenerator.getRegionForecasts().containsKey(regionKey);
        boolean emptyForecastData = region != null && isEmptyForecastData(region);
        boolean invalidForecast = region != null && !isValidForecast(region, gameTime);

        float temperature = 0f;
        float humidity = 0f;
        float pressure = 0f;
        float windDirection = 0f;
        float windSpeed = 0f;

        if (region != null) {
            RegionForecastOrchestrator orchestrator = ForecastOrchestrator.getRegionOrchestrator(level);
            Vec3 local = orchestrator.toRegionLocal(pos);
            temperature = region.sampleTemperature(local, gameTime);
            humidity = region.sampleHumidity(local, gameTime);
            pressure = region.samplePressure(gameTime);
            WindVector forecastWind = region.sampleWind(gameTime);
            if (forecastWind != null) {
                windSpeed = forecastWind.baseSpeed();
                windDirection = normalizeDegrees((float) Math.toDegrees(forecastWind.angleRadians()));
            }
        }

        VerificationStatus status = VerificationStatus.OK;
        if (missingForecast || missingRegion) {
            status = VerificationStatus.MISSING;
            issues.add("Forecast: no region forecast at current position.");
        } else if (invalidForecast || emptyForecastData) {
            status = VerificationStatus.ERROR;
            if (invalidForecast) {
                issues.add("Forecast: sampled values are invalid or non-finite.");
            }
            if (emptyForecastData) {
                issues.add("Forecast: region forecast arrays are empty.");
            }
        }

        return new VerificationReport.ForecastSection(
                status,
                regionCount,
                loadedRegionCount,
                regionKey,
                temperature,
                humidity,
                pressure,
                windDirection,
                windSpeed,
                missingForecast,
                invalidForecast,
                missingRegion,
                emptyForecastData
        );
    }

    private static VerificationReport.AtmosphereSection collectAtmosphere(
            ServerLevel level,
            BlockPos pos,
            RegionInstanceKey regionKey,
            long gameTime,
            VerificationReport.ForecastSection forecast,
            List<String> issues
    ) {
        RegionAtmosphereState live = AtmosphericStateRegistry.getState(regionKey);
        if (live == null) {
            live = AtmosphericStateRegistry.findNearest(pos.getX(), pos.getZ());
        }

        float liveTemp = live != null ? live.getTemperature() : 0f;
        float liveHumidity = live != null ? live.getHumidity() : 0f;
        float livePressure = live != null ? live.getPressure() : 0f;
        float cloudWater = live != null ? live.getCloudWater() : 0f;
        float cloudCover = live != null ? live.getCloudCover() : 0f;
        float rainIntensity = live != null ? live.getRainIntensity() : 0f;
        float windStrength = live != null ? live.getWindStrength() : 0f;
        float windDirection = 0f;
        Float sunlight = live != null ? live.getSunlight() : null;

        if (live != null && live.getWind() != null) {
            windDirection = normalizeDegrees((float) Math.toDegrees(live.getWind().angleRadians()));
        }

        Float forecastTemp = forecast.missingRegion() ? null : forecast.temperatureC();
        Float forecastHumidity = forecast.missingRegion() ? null : forecast.humidity();
        Float forecastPressure = forecast.missingRegion() ? null : forecast.pressureHpa();

        VerificationStatus status = VerificationStatus.OK;
        if (live == null) {
            status = VerificationStatus.MISSING;
            issues.add("Atmosphere: no live atmosphere state near current position.");
        } else if (!isFinite(liveTemp, liveHumidity, livePressure)) {
            status = VerificationStatus.ERROR;
            issues.add("Atmosphere: live scalar values are invalid or non-finite.");
        }

        return new VerificationReport.AtmosphereSection(
                status,
                liveTemp,
                liveHumidity,
                livePressure,
                cloudWater,
                cloudCover,
                rainIntensity,
                windStrength,
                windDirection,
                sunlight,
                forecastTemp,
                forecastHumidity,
                forecastPressure
        );
    }

    private static VerificationReport.WindSection collectWind(
            ServerLevel level,
            RegionInstanceKey regionKey,
            long gameTime,
            List<String> issues
    ) {
        WindVector liveWind = ForecastOrchestrator.getWind(regionKey, gameTime);
        WindRuntimeProbe runtime = WindRuntimeProbe.forRegion(regionKey);
        boolean runtimeExists = runtime.present();
        boolean persistencePresent = runtime.persistencePresent();

        float speed = liveWind != null ? liveWind.baseSpeed() : 0f;
        float direction = liveWind != null
                ? normalizeDegrees((float) Math.toDegrees(liveWind.angleRadians()))
                : 0f;
        float gustStrength = liveWind != null ? liveWind.gustSpeed() : 0f;
        float gustModifier = Math.max(0f, gustStrength - speed);
        boolean gustActive = runtime.gustActive() || gustModifier > 0.05f;

        boolean missingRuntime = !runtimeExists && speed <= 0f;
        boolean invalidState = liveWind != null && !isValidWind(liveWind);

        VerificationStatus status = VerificationStatus.OK;
        if (missingRuntime) {
            status = VerificationStatus.WARNING;
            issues.add("Wind: no runtime wind state for current region.");
        }
        if (invalidState) {
            status = VerificationStatus.ERROR;
            issues.add("Wind: wind vector is invalid or non-finite.");
        }

        return new VerificationReport.WindSection(
                status,
                runtimeExists,
                speed,
                direction,
                gustStrength,
                gustModifier,
                gustActive,
                persistencePresent,
                missingRuntime,
                invalidState
        );
    }

    private static VerificationReport.SeasonSection collectSeason(ServerLevel level, List<String> issues) {
        SeasonSnapshot snapshot = SeasonTimeHelper.snapshot(level);
        CompoundTag driftTag = SeasonalAtmosphericDrift.savePersistentState();
        boolean driftInitialized = driftTag.getBoolean("Initialized");
        boolean driftPersistencePresent = driftInitialized || !driftTag.isEmpty();

        VerificationStatus status = VerificationStatus.OK;
        if (snapshot.stage() == null) {
            status = VerificationStatus.WARNING;
            issues.add("Season: season stage is unavailable.");
        }

        return new VerificationReport.SeasonSection(
                status,
                snapshot.providerId().toString(),
                snapshot.stage(),
                snapshot.progress(),
                snapshot.temperatureOffset(),
                driftInitialized,
                driftPersistencePresent
        );
    }

    private static VerificationReport.WeatherCellSection collectWeatherCells(
            ServerLevel level,
            BlockPos pos,
            List<String> issues
    ) {
        Collection<WeatherCellState> cells = WeatherCellManager.getCells(level);
        int rain = 0;
        int thunder = 0;
        int supercell = 0;
        int active = 0;
        WeatherCellState nearestCell = null;
        double nearestDistance = Double.MAX_VALUE;
        Vec3 sample = Vec3.atCenterOf(pos);

        for (WeatherCellState cell : cells) {
            if (cell == null || !cell.isActive()) {
                continue;
            }
            active++;
            switch (cell.getType()) {
                case RAIN_CELL -> rain++;
                case THUNDERSTORM -> thunder++;
                case SUPERCELL -> supercell++;
                default -> {
                }
            }
            double distance = cell.getCenter().distanceTo(sample);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestCell = cell;
            }
        }

        VerificationReport.NearestWeatherCell nearest = null;
        if (nearestCell != null) {
            nearest = new VerificationReport.NearestWeatherCell(
                    nearestCell.getType(),
                    nearestDistance,
                    nearestCell.getIntensity(),
                    nearestCell.getRadius(),
                    nearestCell.getEvolutionScore(),
                    nearestCell.getSevereEvolutionScore(),
                    nearestCell.getSourceRegion()
            );
        }

        VerificationStatus status = VerificationStatus.OK;
        if (cells.isEmpty()) {
            status = VerificationStatus.WARNING;
        }

        return new VerificationReport.WeatherCellSection(
                status,
                active,
                rain,
                thunder,
                supercell,
                nearest
        );
    }

    private static VerificationReport.CloudSection collectClouds(
            ServerLevel level,
            BlockPos pos,
            List<String> issues
    ) {
        Collection<CloudRegionState> activeRegions = CloudRegionStateStore.getActiveRegions(level);
        int regionCount = activeRegions.size();
        int clusterCount = 0;
        CloudRegionState nearestRegion = null;
        double nearestDistance = Double.MAX_VALUE;
        Vec3 sample = Vec3.atCenterOf(pos);

        for (CloudRegionState region : activeRegions) {
            if (region == null || !region.isActive()) {
                continue;
            }
            clusterCount += region.getClusterCount();
            double distance = region.getCenter().distanceTo(sample);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestRegion = region;
            }
        }

        VerificationReport.NearestCloud nearest = null;
        if (nearestRegion != null) {
            nearest = new VerificationReport.NearestCloud(
                    nearestRegion.getCloudTypeId(),
                    nearestDistance,
                    nearestRegion.getCoverage(),
                    nearestRegion.getDensity()
            );
        }

        VerificationStatus status = regionCount > 0 ? VerificationStatus.OK : VerificationStatus.WARNING;

        return new VerificationReport.CloudSection(
                status,
                regionCount,
                clusterCount,
                nearest
        );
    }

    private static VerificationReport.MorphologySection collectMorphology(
            ServerLevel level,
            int activeRegionCount,
            List<String> issues
    ) {
        EnumMap<CloudMorphologyFamily, Integer> counts = VerificationReport.emptyMorphologyCounts();
        int missingAssignments = 0;
        int unknownAssignments = 0;
        int fallbackAssignments = 0;

        for (CloudRegionState region : CloudRegionStateStore.getActiveRegions(level)) {
            if (region == null || !region.isActive()) {
                continue;
            }
            for (CloudClusterState cluster : region.getClusters()) {
                if (cluster == null || !cluster.isActive()) {
                    continue;
                }
                CloudMorphologyFamily family = cluster.getMorphologyFamily();
                if (family == null) {
                    missingAssignments++;
                    family = CloudMorphologyFamily.PUFF;
                }
                counts.merge(family, 1, Integer::sum);

                String cloudTypeId = cluster.getCloudTypeId();
                if (!CloudTypeRegistry.get(cloudTypeId).isPresent()) {
                    unknownAssignments++;
                    CloudFamily cloudFamily = cluster.getCloudFamily();
                    CloudMorphologyFamily expected = CloudMorphologyFamily.defaultFor(cloudTypeId, cloudFamily);
                    if (family == expected) {
                        fallbackAssignments++;
                    }
                }
            }
            CloudMorphologyFamily regionFamily = region.getMorphologyFamily();
            if (regionFamily == null) {
                missingAssignments++;
            }
        }

        VerificationStatus status = VerificationStatus.OK;
        if (activeRegionCount > 0 && counts.values().stream().mapToInt(Integer::intValue).sum() == 0) {
            status = VerificationStatus.WARNING;
            issues.add("Morphology: active cloud regions have no morphology assignments.");
        }
        if (missingAssignments > 0) {
            status = VerificationStatus.WARNING;
            issues.add("Morphology: " + missingAssignments + " missing family assignments.");
        }
        if (unknownAssignments > 0) {
            issues.add("Morphology: " + unknownAssignments + " unknown family assignments.");
        }

        return new VerificationReport.MorphologySection(
                status,
                counts,
                missingAssignments,
                unknownAssignments,
                fallbackAssignments
        );
    }

    private static VerificationReport.EvolutionSection collectEvolution(
            ServerLevel level,
            BlockPos pos,
            List<String> issues
    ) {
        Map<String, Integer> counts = VerificationReport.emptyEvolutionCounts();
        CloudRegionState nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Vec3 sample = Vec3.atCenterOf(pos);

        for (CloudRegionState region : CloudRegionStateStore.getActiveRegions(level)) {
            if (region == null || !region.isActive()) {
                continue;
            }
            String typeId = normalizeCloudTypeId(region.getCloudTypeId());
            if (counts.containsKey(typeId)) {
                counts.merge(typeId, 1, Integer::sum);
            }
            double distance = region.getCenter().distanceTo(sample);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = region;
            }
        }

        VerificationReport.NearestEvolvingCloud nearestCloud = null;
        if (nearest != null) {
            nearestCloud = new VerificationReport.NearestEvolvingCloud(
                    nearest.getCloudTypeId(),
                    nearest.getMorphologyFamily(),
                    nearest.getDensity(),
                    nearest.getRadius(),
                    nearest.getCoverage(),
                    nearest.getCloudTypeTicks(),
                    nearest.getPreviousCloudTypeId(),
                    nearest.getTransitionBlend()
            );
        }

        VerificationStatus status = VerificationStatus.OK;
        if (nearest == null) {
            status = VerificationStatus.WARNING;
        }

        return new VerificationReport.EvolutionSection(status, counts, nearestCloud);
    }

    private static VerificationReport.PersistenceSection collectPersistence(
            ServerLevel level,
            List<String> issues
    ) {
        FileRegionPersistence forecastPersistence = new FileRegionPersistence(level);
        boolean forecastOnDisk = forecastPersistence.hasRegionData();

        boolean atmosphereLoaded = !AtmosphericStateRegistry.isEmpty();
        CompoundTag driftTag = SeasonalAtmosphericDrift.savePersistentState();
        boolean seasonalDriftPresent = driftTag.getBoolean("Initialized") || driftTag.contains("Stage", Tag.TAG_STRING);

        CompoundTag windTag = WindEngine.savePersistentState();
        boolean windPresent = windTag.contains("States", Tag.TAG_LIST) && !windTag.getList("States", Tag.TAG_COMPOUND).isEmpty();

        boolean weatherCellsPresent = !WeatherCellSavedData.get(level).getCells().isEmpty();
        boolean cloudRegionsPresent = CloudRegionStateStore.size(level) > 0;

        VerificationStatus forecastStatus = forecastOnDisk ? VerificationStatus.OK : VerificationStatus.MISSING;
        VerificationStatus atmosphereStatus = atmosphereLoaded ? VerificationStatus.OK : VerificationStatus.MISSING;
        VerificationStatus seasonalStatus = seasonalDriftPresent ? VerificationStatus.OK : VerificationStatus.WARNING;
        VerificationStatus windStatus = windPresent ? VerificationStatus.OK : VerificationStatus.WARNING;
        VerificationStatus weatherCellStatus = weatherCellsPresent ? VerificationStatus.OK : VerificationStatus.WARNING;
        VerificationStatus cloudStatus = cloudRegionsPresent ? VerificationStatus.OK : VerificationStatus.WARNING;

        VerificationStatus overall = VerificationStatus.OK;
        if (forecastStatus == VerificationStatus.MISSING || atmosphereStatus == VerificationStatus.MISSING) {
            overall = VerificationStatus.WARNING;
        }

        return new VerificationReport.PersistenceSection(
                overall,
                forecastStatus,
                atmosphereStatus,
                seasonalStatus,
                windStatus,
                weatherCellStatus,
                cloudStatus
        );
    }

    private static boolean isEmptyForecastData(ForecastRegion region) {
        float[][] temperature = region.getTemperature();
        float[][] humidity = region.getHumidity();
        float[][] pressure = region.getPressure();
        WindVector[] wind = region.getWind();
        return isEmptyMatrix(temperature) && isEmptyMatrix(humidity) && isEmptyMatrix(pressure)
                && (wind == null || wind.length == 0);
    }

    private static boolean isEmptyMatrix(float[][] matrix) {
        if (matrix == null || matrix.length == 0) {
            return true;
        }
        for (float[] row : matrix) {
            if (row != null && row.length > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidForecast(ForecastRegion region, long gameTime) {
        Vec3 center = new Vec3(region.getKey().regionSize() / 2.0, 0.0, region.getKey().regionSize() / 2.0);
        return isFinite(
                region.sampleTemperature(center, gameTime),
                region.sampleHumidity(center, gameTime),
                region.samplePressure(gameTime)
        );
    }

    private static boolean isValidWind(WindVector wind) {
        return wind != null
                && Float.isFinite(wind.baseSpeed())
                && Float.isFinite(wind.gustSpeed())
                && Float.isFinite(wind.angleRadians());
    }

    private static boolean isFinite(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static float normalizeDegrees(float degrees) {
        return (degrees % 360f + 360f) % 360f;
    }

    private static String normalizeCloudTypeId(String cloudTypeId) {
        if (cloudTypeId == null || cloudTypeId.isBlank()) {
            return CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        }
        return cloudTypeId.trim().toLowerCase(Locale.ROOT);
    }

    private static final class WindRuntimeProbe {
        private final boolean present;
        private final boolean gustActive;
        private final boolean persistencePresent;

        private WindRuntimeProbe(boolean present, boolean gustActive, boolean persistencePresent) {
            this.present = present;
            this.gustActive = gustActive;
            this.persistencePresent = persistencePresent;
        }

        static WindRuntimeProbe forRegion(RegionInstanceKey regionKey) {
            CompoundTag root = WindEngine.savePersistentState();
            ListTag states = root.getList("States", Tag.TAG_COMPOUND);
            boolean persistencePresent = !states.isEmpty();
            if (regionKey == null) {
                return new WindRuntimeProbe(false, false, persistencePresent);
            }
            for (int i = 0; i < states.size(); i++) {
                CompoundTag entry = states.getCompound(i);
                RegionInstanceKey key = loadRegionKey(entry.getCompound("Region"));
                if (!regionKey.equals(key)) {
                    continue;
                }
                CompoundTag state = entry.getCompound("State");
                boolean gustActive = state.getBoolean("GustActive");
                return new WindRuntimeProbe(true, gustActive, persistencePresent);
            }
            return new WindRuntimeProbe(false, false, persistencePresent);
        }

        boolean present() {
            return present;
        }

        boolean gustActive() {
            return gustActive;
        }

        boolean persistencePresent() {
            return persistencePresent;
        }

        private static RegionInstanceKey loadRegionKey(CompoundTag tag) {
            if (tag == null || !tag.contains("RegionX", Tag.TAG_INT) || !tag.contains("RegionZ", Tag.TAG_INT)) {
                return null;
            }
            int size = tag.contains("RegionSize", Tag.TAG_INT)
                    ? tag.getInt("RegionSize")
                    : RegionInstanceKey.DEFAULT_REGION_SIZE;
            return new RegionInstanceKey(tag.getInt("RegionX"), tag.getInt("RegionZ"), size);
        }
    }
}

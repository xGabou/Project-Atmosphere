package net.Gabou.projectatmosphere.telemetry.verification;

import net.Gabou.projectatmosphere.clouds.backend.CloudBackendMigrationManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendStatus;
import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderDataFactory;
import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericSupportEvaluator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericUpdateScheduler;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneManager;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.atmosphere.SeasonalAtmosphericDrift;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.OceanBasinManager;
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
import net.minecraft.util.Mth;
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
        VerificationReport.CloudBackendSection cloudBackend = collectCloudBackend(level, issues);
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
                cloudBackend,
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
        Float baseForecastTemp = null;
        Float effectiveForecastTemp = null;
        Float deltaToBaseForecast = null;
        Float deltaToEffectiveForecast = null;
        Float schedulerTemperatureDelta = null;
        Float baseRelaxTemperatureDelta = null;
        Float seasonalDriftTemperatureDelta = null;
        Float forecastHumidity = forecast.missingRegion() ? null : forecast.humidity();
        Float forecastPressure = forecast.missingRegion() ? null : forecast.pressureHpa();
        Float pressureTarget = null;
        Float forecastPressureCurrentSample = null;
        Float liveStateRawPressureTarget = null;
        Float effectivePressureTarget = null;
        String pressureTargetSource = null;
        Integer pressureTargetDayIndex = null;
        Integer currentForecastDayIndex = null;
        Boolean pressureTargetUsesCurrentForecastDay = null;
        Boolean day0PressureTargetProfileActive = null;
        Boolean stalePressureTargetDetected = null;
        Float stalePressureTargetCorrectionDelta = null;
        String pressureAnomalyClassification = null;
        Float normalPressureReference = null;
        Float pressureDeltaToForecast = null;
        Float pressureDeltaToNormal = null;
        Float schedulerPressureDelta = null;
        Float forecastRecoveryPressureDelta = null;
        Float pressureGuardDelta = null;
        Float baseRelaxPressureDelta = null;
        Float rainPressureDelta = null;
        Float windPressureMixDelta = null;
        Float oceanFlux = null;
        Float oceanPressureInfluence = null;
        Float cyclonePressureInfluence = null;
        Float stormPressureSupport = null;
        Float thunderstormSupport = null;
        Float seasonPressureOffset = null;
        Float seasonTemperatureOffset = null;
        Boolean pressureRecoveryEligible = null;
        Boolean cycloneSeedEligible = null;
        Float cycloneSeedSupport = null;
        Float cycloneIntensificationSupport = null;
        Float cycloneSevereSupport = null;
        Boolean unsupportedLowRecoveryActive = null;
        Float unsupportedLowRecoveryDelta = null;
        Float unsupportedLowRecoveryCapPerDay = null;
        Float supportResistance = null;

        if (live != null) {
            boolean active = AtmosphericStateRegistry.getActiveStates().contains(live.getRegionId());
            long dayTime = level.getDayTime();
            AtmosphericUpdateScheduler.PressureDiagnostics pressureDiagnostics =
                    AtmosphericUpdateScheduler.estimatePressureDiagnostics(live, gameTime, active);
            AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(live.getRegionId(), live);
            CycloneManager.CycloneSupport cycloneSupport = CycloneManager.evaluateCycloneSupport(live, gameTime);
            RegionAtmosphereState.PressureTargetDebug pressureTargetDebug = live.pressureTargetDebug(gameTime);
            baseForecastTemp = live.getBaseTargetTemperature(dayTime);
            effectiveForecastTemp = live.getTargetTemperature(dayTime);
            deltaToBaseForecast = liveTemp - baseForecastTemp;
            deltaToEffectiveForecast = liveTemp - effectiveForecastTemp;
            float updateScale = active ? 1.0F : 0.35F;
            float relaxFactor = active ? 0.0012F : 0.00035F;
            float driftRate = active ? 0.025F : 0.008F;
            float driftMaxStep = active ? 0.25F : 0.08F;
            schedulerTemperatureDelta = (effectiveForecastTemp - liveTemp) * 0.04F * updateScale;
            baseRelaxTemperatureDelta = (live.getEffectiveBaseTemperature() - liveTemp) * relaxFactor;
            seasonalDriftTemperatureDelta = Mth.clamp((effectiveForecastTemp - liveTemp) * driftRate, -driftMaxStep, driftMaxStep);
            pressureTarget = pressureDiagnostics.targetPressure();
            forecastPressureCurrentSample = pressureTargetDebug.forecastPressureCurrentSample();
            liveStateRawPressureTarget = pressureTargetDebug.rawTargetPressure();
            effectivePressureTarget = pressureTargetDebug.effectiveTargetPressure();
            pressureTargetSource = pressureTargetDebug.source();
            pressureTargetDayIndex = pressureTargetDebug.targetDayIndex();
            currentForecastDayIndex = pressureTargetDebug.currentForecastDayIndex();
            pressureTargetUsesCurrentForecastDay = pressureTargetDebug.targetUsesCurrentForecastDay();
            day0PressureTargetProfileActive = pressureTargetDebug.day0TargetProfileActive();
            stalePressureTargetDetected = pressureTargetDebug.staleTargetDetected();
            stalePressureTargetCorrectionDelta = pressureTargetDebug.staleTargetCorrectionDelta();
            normalPressureReference = pressureDiagnostics.normalPressureReference();
            pressureDeltaToForecast = forecastPressure == null ? null : livePressure - forecastPressure;
            pressureDeltaToNormal = livePressure - pressureDiagnostics.normalPressureReference();
            schedulerPressureDelta = pressureDiagnostics.schedulerPressureDelta();
            forecastRecoveryPressureDelta = pressureDiagnostics.forecastRecoveryDelta();
            pressureGuardDelta = pressureDiagnostics.pressureGuardDelta();
            baseRelaxPressureDelta = pressureDiagnostics.baseRelaxDelta();
            rainPressureDelta = pressureDiagnostics.rainPressureDelta();
            windPressureMixDelta = WindVector.estimatePressureTransport(live.getRegionId());
            oceanFlux = OceanBasinManager.estimateHumidityFlux(live.getRegionId(), liveHumidity);
            oceanPressureInfluence = OceanBasinManager.estimatePressureDelta(live.getRegionId(), livePressure);
            cyclonePressureInfluence = CycloneManager.estimatePressureDelta(live, gameTime);
            pressureAnomalyClassification = classifyPressureAnomaly(
                    pressureTargetDebug,
                    pressureDiagnostics,
                    windPressureMixDelta,
                    oceanPressureInfluence,
                    cyclonePressureInfluence,
                    support
            );
            stormPressureSupport = support.stormPressureSupport();
            thunderstormSupport = support.thunderstormSupport();
            seasonPressureOffset = SeasonalAtmosphericDrift.currentPressureOffsetHpa();
            seasonTemperatureOffset = SeasonalAtmosphericDrift.currentTemperatureOffsetC();
            pressureRecoveryEligible = pressureDiagnostics.supportResistance() < 0.65F
                    && livePressure < pressureDiagnostics.normalPressureReference();
            cycloneSeedEligible = cycloneSupport.seedEligible();
            cycloneSeedSupport = cycloneSupport.seedSupport();
            cycloneIntensificationSupport = cycloneSupport.intensificationSupport();
            cycloneSevereSupport = cycloneSupport.severeSupport();
            unsupportedLowRecoveryActive = pressureDiagnostics.unsupportedLowRecoveryActive();
            unsupportedLowRecoveryDelta = pressureDiagnostics.unsupportedLowRecoveryDelta();
            unsupportedLowRecoveryCapPerDay = pressureDiagnostics.unsupportedLowRecoveryCapPerDay();
            supportResistance = pressureDiagnostics.supportResistance();
        }

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
                baseForecastTemp,
                effectiveForecastTemp,
                deltaToBaseForecast,
                deltaToEffectiveForecast,
                schedulerTemperatureDelta,
                baseRelaxTemperatureDelta,
                seasonalDriftTemperatureDelta,
                forecastHumidity,
                forecastPressure,
                pressureTarget,
                forecastPressureCurrentSample,
                liveStateRawPressureTarget,
                effectivePressureTarget,
                pressureTargetSource,
                pressureTargetDayIndex,
                currentForecastDayIndex,
                pressureTargetUsesCurrentForecastDay,
                day0PressureTargetProfileActive,
                stalePressureTargetDetected,
                stalePressureTargetCorrectionDelta,
                pressureAnomalyClassification,
                normalPressureReference,
                pressureDeltaToForecast,
                pressureDeltaToNormal,
                schedulerPressureDelta,
                forecastRecoveryPressureDelta,
                pressureGuardDelta,
                baseRelaxPressureDelta,
                rainPressureDelta,
                windPressureMixDelta,
                oceanFlux,
                oceanPressureInfluence,
                cyclonePressureInfluence,
                stormPressureSupport,
                thunderstormSupport,
                seasonPressureOffset,
                seasonTemperatureOffset,
                pressureRecoveryEligible,
                cycloneSeedEligible,
                cycloneSeedSupport,
                cycloneIntensificationSupport,
                cycloneSevereSupport,
                unsupportedLowRecoveryActive,
                unsupportedLowRecoveryDelta,
                unsupportedLowRecoveryCapPerDay,
                supportResistance
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
                SeasonalAtmosphericDrift.currentTemperatureOffsetC(),
                SeasonalAtmosphericDrift.currentPressureOffsetHpa(),
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

    private static VerificationReport.CloudBackendSection collectCloudBackend(
            ServerLevel level,
            List<String> issues
    ) {
        CloudBackendStatus status = CloudBackendMigrationManager.status(level);
        if (status.duplicateVisualCloudRisk()) {
            issues.add("Cloud Backend: PA native clouds are marked rendered while Simple Clouds owns the visual backend.");
        }
        return new VerificationReport.CloudBackendSection(
                status.currentBackend(),
                status.lastBackend(),
                status.simpleCloudsLoaded(),
                status.paCloudsStored(),
                status.paCloudsRendered(),
                status.bridgeSnapshotsStored(),
                status.lastMigrationDirection(),
                status.migrationStatus(),
                status.duplicateVisualCloudRisk()
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
            CloudClusterState cluster = selectPrimaryCluster(nearest);
            CloudRegionRenderData renderData = CloudRegionRenderDataFactory.create(nearest, level.getGameTime());
            CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(nearest.getCloudTypeId());
            CloudShapeProfile shape = definition.getShapeProfile();
            Vec3 center = nearest.getCenter();
            Vec3 previousCenter = nearest.getPreviousCenter();
            Vec3 velocity = nearest.getVelocity();
            RegionInstanceKey regionKey = RegionInstanceKey.from(BlockPos.containing(center));
            RegionAtmosphereState atmosphere = AtmosphericStateRegistry.getState(regionKey);
            AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(regionKey, atmosphere);
            float targetRadius = Math.max(shape.getBaseRadius(), nearest.getRadius());
            float targetCoverage = renderData.getCoverage();
            float targetDensity = renderData.getDensity();
            float driftSpeed = (float) Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
            float windCoupling = safeCloudWindDriftScale();
            String radiusCap = resolveRadiusCap(nearest);
            String growthBlockedReason = resolveGrowthBlockedReason(nearest, radiusCap);
            String motionSource = resolveCloudMotionSource(velocity);
            int ageTicks = nearest.getAgeTicks();
            nearestCloud = new VerificationReport.NearestEvolvingCloud(
                    nearest.getCloudTypeId(),
                    nearest.getMorphologyFamily(),
                    nearest.getDensity(),
                    nearest.getRadius(),
                    nearest.getCoverage(),
                    nearest.getCloudTypeTicks(),
                    nearest.getPreviousCloudTypeId(),
                    nearest.getTransitionBlend(),
                    nearest.getRadius(),
                    renderData.getRadius(),
                    targetRadius,
                    radiusCap,
                    targetRadius - nearest.getRadius(),
                    nearest.getGrowth() < 1.0F ? 1.0F / 600.0F : 0.0F,
                    growthBlockedReason,
                    nearest.getCoverage(),
                    renderData.getCoverage(),
                    targetCoverage,
                    nearest.getDensity(),
                    renderData.getDensity(),
                    targetDensity,
                    support.hasState() ? Math.max(support.thunderstormSupport(), support.rainSupport()) : 0.0F,
                    support.hasState() ? support.cloudBirthBaseScore() : 0.0F,
                    nearest.getGrowth() < 1.0F ? "lifecycle growth" : nearest.getDecay() > 0.0F ? "decay" : "mature",
                    "PA_NATIVE migration/source=" + (nearest.getSourceRegionKey() == null ? "unknown" : nearest.getSourceRegionKey()),
                    nearest.getRegionId().toString(),
                    formatVec(center),
                    formatVec(previousCenter),
                    formatVec(velocity),
                    driftSpeed,
                    windCoupling,
                    motionSource,
                    ageTicks,
                    ageTicks,
                    level.getGameTime(),
                    nearest.getCloudSeed(),
                    true,
                    formatBounds(renderData),
                    "server-source; client LOD selected per frame"
            );
        }

        VerificationStatus status = VerificationStatus.OK;
        if (nearest == null) {
            status = VerificationStatus.WARNING;
        }

        return new VerificationReport.EvolutionSection(status, counts, nearestCloud);
    }

    private static CloudClusterState selectPrimaryCluster(CloudRegionState region) {
        CloudClusterState best = null;
        float bestWeight = -1.0F;
        if (region == null) {
            return null;
        }
        for (CloudClusterState cluster : region.getClusters()) {
            if (cluster == null || !cluster.isActive()) {
                continue;
            }
            float weight = cluster.getFootprint();
            if (best == null || weight > bestWeight) {
                best = cluster;
                bestWeight = weight;
            }
        }
        return best;
    }

    private static String resolveRadiusCap(CloudRegionState region) {
        if (region == null) {
            return "unknown";
        }
        if (region.getRadius() >= 1399.5F) {
            return "radius capped by migration (Simple Clouds -> PA_NATIVE clamp 1400.00)";
        }
        return "not capped by migration; raw radius from cloud type geometry/cluster aggregation";
    }

    private static String resolveGrowthBlockedReason(CloudRegionState region, String radiusCap) {
        if (region == null) {
            return "unknown";
        }
        if (radiusCap != null && radiusCap.contains("migration")) {
            return "raw radius fixed at migration cap; lifecycle growth affects visual alpha only";
        }
        if (region.getGrowth() >= 1.0F) {
            return "growth complete; radius/coverage/density are stable backend geometry values";
        }
        return "lifecycle growth active; raw radius is not resized by lifecycle growth";
    }

    private static String resolveCloudMotionSource(Vec3 velocity) {
        if (velocity == null || velocity.lengthSqr() <= 0.0000001D) {
            return "server wind drift inactive or no resolved wind velocity";
        }
        return "server regional wind drift; client extrapolates between sync packets";
    }

    private static float safeCloudWindDriftScale() {
        try {
            return AtmoCommonConfig.CLOUD_WIND_DRIFT_SCALE.get().floatValue();
        } catch (IllegalStateException exception) {
            return 0.035F;
        }
    }

    private static String formatVec(Vec3 vec) {
        if (vec == null) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec.x(), vec.y(), vec.z());
    }

    private static String formatBounds(CloudRegionRenderData data) {
        if (data == null) {
            return "unknown";
        }
        Vec3 center = data.getCenter();
        float radius = data.getRadius();
        return String.format(
                Locale.ROOT,
                "x=%.1f..%.1f y=%.1f..%.1f z=%.1f..%.1f",
                center.x() - radius,
                center.x() + radius,
                data.getBaseY(),
                data.getTopY(),
                center.z() - radius,
                center.z() + radius
        );
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

    private static String classifyPressureAnomaly(
            RegionAtmosphereState.PressureTargetDebug target,
            AtmosphericUpdateScheduler.PressureDiagnostics pressure,
            Float windPressure,
            Float oceanPressure,
            Float cyclonePressure,
            AtmosphericSupportEvaluator.Support support
    ) {
        if (target == null) {
            return "unknown";
        }
        if (target.staleTargetDetected() && pressure != null && pressure.supportResistance() < 0.65F) {
            return "stale unsupported target";
        }
        if (cyclonePressure != null && cyclonePressure < -0.05F) {
            return "cyclone seed";
        }
        if (support != null
                && (support.rainSupport() >= 0.45F
                || support.thunderstormSupport() >= 0.35F
                || support.supercellSupport() >= 0.25F)) {
            return "rain/storm system";
        }
        if (windPressure != null && windPressure < -0.05F) {
            return "wind-imported gradient";
        }
        if (oceanPressure != null && oceanPressure < -0.05F) {
            return "ocean-influenced low";
        }
        if (target.effectiveTargetPressure() < 1008.0F) {
            return "active forecast anomaly";
        }
        return "current forecast";
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

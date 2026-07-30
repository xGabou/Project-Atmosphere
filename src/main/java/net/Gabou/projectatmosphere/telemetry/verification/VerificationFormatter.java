package net.Gabou.projectatmosphere.telemetry.verification;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.util.UnitFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VerificationFormatter {
    private VerificationFormatter() {
    }

    public static String formatFull(VerificationReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("=== Project Atmosphere Verification ===");
        lines.add("GameTime: " + report.gameTime());
        lines.add("WorldTime: " + report.worldTime());
        lines.add("");
        appendForecast(lines, report.forecast());
        lines.add("");
        appendAtmosphere(lines, report.atmosphere());
        lines.add("");
        appendWind(lines, report.wind());
        lines.add("");
        appendSeason(lines, report.season());
        lines.add("");
        appendWeatherCells(lines, report.weatherCells());
        lines.add("");
        appendClouds(lines, report.clouds());
        lines.add("");
        appendCloudBackend(lines, report.cloudBackend());
        lines.add("");
        appendPaNativeBackend(lines, report.paNativeBackend());
        lines.add("");
        appendMorphology(lines, report.morphology());
        lines.add("");
        appendEvolution(lines, report.evolution());
        lines.add("");
        appendNearestNativeCloud(lines, report.nearestNativeCloud());
        lines.add("");
        appendPersistence(lines, report.persistence());
        if (!report.issues().isEmpty()) {
            lines.add("");
            lines.add("Issues:");
            for (String issue : report.issues()) {
                lines.add("- " + issue);
            }
        }
        return String.join("\n", lines);
    }

    public static String formatSnapshot(VerificationReport report) {
        List<String> lines = new ArrayList<>();
        VerificationReport.ForecastSection forecast = report.forecast();
        VerificationReport.AtmosphereSection atmosphere = report.atmosphere();
        VerificationReport.WindSection wind = report.wind();
        VerificationReport.SeasonSection season = report.season();
        VerificationReport.WeatherCellSection cells = report.weatherCells();
        VerificationReport.CloudSection clouds = report.clouds();
        VerificationReport.CloudBackendSection cloudBackend = report.cloudBackend();
        VerificationReport.PaNativeBackendSection paNativeBackend = report.paNativeBackend();
        VerificationReport.PersistenceSection persistence = report.persistence();

        lines.add("PA_VERIFY_SNAPSHOT");
        lines.add("gameTime=" + report.gameTime());
        lines.add("worldTime=" + report.worldTime());
        lines.add("forecast.regions=" + forecast.regionCount());
        lines.add("forecast.loaded=" + forecast.loadedRegionCount());
        lines.add("forecast.region=" + forecast.currentRegion());
        lines.add("forecast.status=" + forecast.status().label());
        lines.add("forecast.tempC=" + formatNumber(forecast.temperatureC()));
        lines.add("forecast.humidity=" + formatNumber(forecast.humidity() / 100f));
        lines.add("forecast.pressure=" + formatNumber(forecast.pressureHpa()));
        lines.add("forecast.windDir=" + formatNumber(forecast.windDirectionDeg()));
        lines.add("forecast.windSpeed=" + formatNumber(forecast.windSpeedMps()));
        lines.add("atmosphere.status=" + atmosphere.status().label());
        lines.add("atmosphere.tempC=" + formatNumber(atmosphere.liveTemperatureC()));
        addNullable(lines, "atmosphere.baseForecastTempC", atmosphere.baseForecastTemperatureC());
        addNullable(lines, "atmosphere.effectiveForecastTempC", atmosphere.effectiveForecastTemperatureC());
        addNullable(lines, "atmosphere.deltaToBaseForecastC", atmosphere.deltaToBaseForecastC());
        addNullable(lines, "atmosphere.deltaToEffectiveForecastC", atmosphere.deltaToEffectiveForecastC());
        addNullable(lines, "atmosphere.schedulerTemperatureDeltaC", atmosphere.schedulerTemperatureDeltaC());
        addNullable(lines, "atmosphere.baseRelaxTemperatureDeltaC", atmosphere.baseRelaxTemperatureDeltaC());
        addNullable(lines, "atmosphere.seasonalDriftTemperatureDeltaC", atmosphere.seasonalDriftTemperatureDeltaC());
        lines.add("atmosphere.humidity=" + formatNumber(atmosphere.liveHumidity()));
        lines.add("atmosphere.pressure=" + formatNumber(atmosphere.livePressureHpa()));
        lines.add("atmosphere.cloudWater=" + formatNumber(atmosphere.cloudWater()));
        lines.add("atmosphere.cloudCover=" + formatNumber(atmosphere.cloudCover()));
        lines.add("atmosphere.rain=" + formatNumber(atmosphere.rainIntensity()));
        lines.add("atmosphere.windStrength=" + formatNumber(atmosphere.windStrength()));
        lines.add("atmosphere.windDir=" + formatNumber(atmosphere.windDirectionDeg()));
        addNullable(lines, "atmosphere.pressureTarget", atmosphere.pressureTargetHpa());
        addNullable(lines, "atmosphere.forecastPressureCurrentSample", atmosphere.forecastPressureCurrentSampleHpa());
        addNullable(lines, "atmosphere.liveStateRawPressureTarget", atmosphere.liveStateRawPressureTargetHpa());
        addNullable(lines, "atmosphere.effectivePressureTarget", atmosphere.effectivePressureTargetHpa());
        addNullable(lines, "atmosphere.pressureTargetSource", atmosphere.pressureTargetSource());
        addNullable(lines, "atmosphere.pressureTargetDayIndex", atmosphere.pressureTargetDayIndex());
        addNullable(lines, "atmosphere.currentForecastDayIndex", atmosphere.currentForecastDayIndex());
        addNullable(lines, "atmosphere.pressureTargetUsesCurrentForecastDay", atmosphere.pressureTargetUsesCurrentForecastDay());
        addNullable(lines, "atmosphere.day0PressureTargetProfileActive", atmosphere.day0PressureTargetProfileActive());
        addNullable(lines, "atmosphere.stalePressureTargetDetected", atmosphere.stalePressureTargetDetected());
        addNullable(lines, "atmosphere.stalePressureTargetCorrectionDelta", atmosphere.stalePressureTargetCorrectionDelta());
        addNullable(lines, "atmosphere.pressureAnomalyClassification", atmosphere.pressureAnomalyClassification());
        addNullable(lines, "atmosphere.normalPressure", atmosphere.normalPressureReferenceHpa());
        addNullable(lines, "atmosphere.pressureDeltaForecast", atmosphere.pressureDeltaToForecastHpa());
        addNullable(lines, "atmosphere.pressureDeltaNormal", atmosphere.pressureDeltaToNormalHpa());
        addNullable(lines, "atmosphere.schedulerPressureDelta", atmosphere.schedulerPressureDelta());
        addNullable(lines, "atmosphere.pressureForecastRecovery", atmosphere.forecastRecoveryPressureDelta());
        addNullable(lines, "atmosphere.pressureGuard", atmosphere.pressureGuardDelta());
        addNullable(lines, "atmosphere.pressureBaseRelax", atmosphere.baseRelaxPressureDelta());
        addNullable(lines, "atmosphere.rainPressureDelta", atmosphere.rainPressureDelta());
        addNullable(lines, "atmosphere.windPressureMix", atmosphere.windPressureMixDelta());
        addNullable(lines, "atmosphere.oceanFlux", atmosphere.oceanFlux());
        addNullable(lines, "atmosphere.oceanPressureInfluence", atmosphere.oceanPressureInfluence());
        addNullable(lines, "atmosphere.cyclonePressureInfluence", atmosphere.cyclonePressureInfluence());
        addNullable(lines, "atmosphere.stormPressureSupport", atmosphere.stormPressureSupport());
        addNullable(lines, "atmosphere.thunderstormSupport", atmosphere.thunderstormSupport());
        addNullable(lines, "atmosphere.seasonPressureOffset", atmosphere.seasonPressureOffsetHpa());
        addNullable(lines, "atmosphere.seasonTemperatureOffset", atmosphere.seasonTemperatureOffsetC());
        if (atmosphere.pressureRecoveryEligible() != null) {
            lines.add("atmosphere.pressureRecoveryEligible=" + atmosphere.pressureRecoveryEligible());
        }
        if (atmosphere.cycloneSeedEligible() != null) {
            lines.add("atmosphere.cycloneSeedEligible=" + atmosphere.cycloneSeedEligible());
        }
        addNullable(lines, "atmosphere.cycloneSeedSupport", atmosphere.cycloneSeedSupport());
        addNullable(lines, "atmosphere.cycloneIntensificationSupport", atmosphere.cycloneIntensificationSupport());
        addNullable(lines, "atmosphere.cycloneSevereSupport", atmosphere.cycloneSevereSupport());
        if (atmosphere.unsupportedLowRecoveryActive() != null) {
            lines.add("atmosphere.unsupportedLowRecoveryActive=" + atmosphere.unsupportedLowRecoveryActive());
        }
        addNullable(lines, "atmosphere.unsupportedLowRecoveryDelta", atmosphere.unsupportedLowRecoveryDelta());
        addNullable(lines, "atmosphere.unsupportedLowRecoveryCapPerDay", atmosphere.unsupportedLowRecoveryCapPerDay());
        addNullable(lines, "atmosphere.supportResistance", atmosphere.supportResistance());
        if (atmosphere.sunlight() != null) {
            lines.add("atmosphere.sunlight=" + formatNumber(atmosphere.sunlight()));
        }
        lines.add("wind.status=" + wind.status().label());
        lines.add("wind.speed=" + formatNumber(wind.speedMps()));
        lines.add("wind.direction=" + formatNumber(wind.directionDeg()));
        lines.add("wind.gustActive=" + wind.gustActive());
        lines.add("wind.gustModifier=" + formatNumber(wind.gustModifier()));
        lines.add("season.provider=" + season.providerId());
        lines.add("season.stage=" + season.stage());
        lines.add("season.progress=" + formatNumber(season.progress()));
        lines.add("season.tempOffset=" + formatNumber(season.temperatureOffset()));
        lines.add("season.driftTempOffset=" + formatNumber(season.driftTemperatureOffset()));
        lines.add("season.driftPressureOffset=" + formatNumber(season.driftPressureOffset()));
        lines.add("season.driftInitialized=" + season.driftInitialized());
        lines.add("weatherCells.active=" + cells.totalActive());
        lines.add("weatherCells.rain=" + cells.rainCellCount());
        lines.add("weatherCells.thunder=" + cells.thunderstormCount());
        lines.add("weatherCells.supercell=" + cells.supercellCount());
        lines.add("clouds.regions=" + clouds.activeRegionCount());
        lines.add("clouds.clusters=" + clouds.activeClusterCount());
        lines.add("cloudBackend.current=" + cloudBackend.currentVisualBackend());
        lines.add("cloudBackend.last=" + cloudBackend.lastVisualBackend());
        lines.add("cloudBackend.simpleCloudsLoaded=" + cloudBackend.simpleCloudsLoaded());
        lines.add("cloudBackend.paStored=" + cloudBackend.paNativeCloudsStored());
        lines.add("cloudBackend.paRendered=" + cloudBackend.paNativeCloudsRendered());
        lines.add("cloudBackend.bridgeSnapshots=" + cloudBackend.bridgeSnapshotsStored());
        lines.add("cloudBackend.lastMigrationDirection=" + cloudBackend.lastMigrationDirection());
        lines.add("cloudBackend.duplicateRisk=" + cloudBackend.duplicateVisualCloudRisk());
        lines.add("cloudBackend.migrationStatus=" + cloudBackend.migrationStatus().label());
        lines.add("paNative.enabled=" + paNativeBackend.paNativeEnabled());
        lines.add("paNative.cloudsStored=" + paNativeBackend.paNativeCloudsStored());
        lines.add("paNative.cloudsSynced=" + paNativeBackend.paNativeCloudsSynced());
        lines.add("paNative.cloudsRendered=" + paNativeBackend.paNativeCloudsRendered());
        lines.add("paNative.syncActive=" + paNativeBackend.nativeCloudSyncActive());
        lines.add("paNative.renderActive=" + paNativeBackend.nativeCloudRenderActive());
        lines.add("paNative.shadowActive=" + paNativeBackend.nativeCloudShadowActive());
        lines.add("paNative.fallbackDarkeningActive=" + paNativeBackend.fallbackDarkeningActive());
        lines.add("paNative.lightingMetadataActive=" + paNativeBackend.lightingMetadataActive());
        lines.add("paNative.dhMetadataActive=" + paNativeBackend.dhMetadataActive());
        for (CloudMorphologyFamily family : CloudMorphologyFamily.values()) {
            int count = report.morphology().countsByFamily().getOrDefault(family, 0);
            lines.add("morphology." + family.name() + "=" + count);
        }
        for (Map.Entry<String, Integer> entry : report.evolution().countsByType().entrySet()) {
            lines.add("evolution." + entry.getKey() + "=" + entry.getValue());
        }
        VerificationReport.NearestNativeCloud nativeCloud = report.nearestNativeCloud();
        if (nativeCloud != null) {
            lines.add("nativeCloud.id=" + nativeCloud.cloudId());
            lines.add("nativeCloud.type=" + nativeCloud.type());
            lines.add("nativeCloud.position=" + nativeCloud.position());
            lines.add("nativeCloud.previousPosition=" + nativeCloud.previousPosition());
            lines.add("nativeCloud.velocity=" + nativeCloud.velocity());
            lines.add("nativeCloud.windCoupling=" + formatNumber(nativeCloud.windCoupling()));
            lines.add("nativeCloud.motionActive=" + nativeCloud.motionActive());
            lines.add("nativeCloud.motionBlockedReason=" + nativeCloud.motionBlockedReason());
            lines.add("nativeCloud.radius=" + formatNumber(nativeCloud.radius()));
            lines.add("nativeCloud.targetRadius=" + formatNumber(nativeCloud.targetRadius()));
            lines.add("nativeCloud.renderedRadius=" + formatNumber(nativeCloud.renderedRadius()));
            lines.add("nativeCloud.radiusCap=" + formatNumber(nativeCloud.radiusCap()));
            lines.add("nativeCloud.coverage=" + formatNumber(nativeCloud.coverage()));
            lines.add("nativeCloud.targetCoverage=" + formatNumber(nativeCloud.targetCoverage()));
            lines.add("nativeCloud.density=" + formatNumber(nativeCloud.density()));
            lines.add("nativeCloud.targetDensity=" + formatNumber(nativeCloud.targetDensity()));
            lines.add("nativeCloud.growthRate=" + formatNumber(nativeCloud.growthRate()));
            lines.add("nativeCloud.growthActive=" + nativeCloud.growthActive());
            lines.add("nativeCloud.growthBlockedReason=" + nativeCloud.growthBlockedReason());
            lines.add("nativeCloud.age=" + nativeCloud.age());
            lines.add("nativeCloud.lifetime=" + nativeCloud.lifetime());
            lines.add("nativeCloud.lastStateTick=" + nativeCloud.lastStateTick());
            lines.add("nativeCloud.lastSyncTick=" + nativeCloud.lastSyncTick());
            lines.add("nativeCloud.lastRenderSnapshotTick=" + nativeCloud.lastRenderSnapshotTick());
            lines.add("nativeCloud.renderBounds=" + nativeCloud.renderBounds());
        }
        lines.add("persistence.forecast=" + persistence.forecast().label());
        lines.add("persistence.atmosphere=" + persistence.atmosphere().label());
        lines.add("persistence.seasonalDrift=" + persistence.seasonalDrift().label());
        lines.add("persistence.wind=" + persistence.wind().label());
        lines.add("persistence.weatherCells=" + persistence.weatherCells().label());
        lines.add("persistence.cloudRegions=" + persistence.cloudRegions().label());
        return String.join("\n", lines);
    }

    private static void appendForecast(List<String> lines, VerificationReport.ForecastSection forecast) {
        lines.add("Forecast:");
        lines.add("Regions: " + forecast.regionCount());
        lines.add("Loaded: " + forecast.loadedRegionCount());
        lines.add("Current Region: " + forecast.currentRegion());
        lines.add("Status: " + forecast.status().label());
        lines.add("Temperature: " + UnitFormatter.formatTemperature(forecast.temperatureC()));
        lines.add("Humidity: " + UnitFormatter.formatHumidity(forecast.humidity()));
        lines.add("Pressure: " + UnitFormatter.formatPressure(forecast.pressureHpa()));
        lines.add("Wind Direction: " + formatDegrees(forecast.windDirectionDeg()));
        lines.add("Wind Speed: " + UnitFormatter.formatWindSpeed(forecast.windSpeedMps()));
        if (forecast.missingForecast()) {
            lines.add("Missing Forecast: yes");
        }
        if (forecast.missingRegion()) {
            lines.add("Missing Region: yes");
        }
        if (forecast.emptyForecastData()) {
            lines.add("Empty Forecast Data: yes");
        }
        if (forecast.invalidForecast()) {
            lines.add("Invalid Forecast: yes");
        }
    }

    private static void appendAtmosphere(List<String> lines, VerificationReport.AtmosphereSection atmosphere) {
        lines.add("Atmosphere:");
        lines.add("Status: " + atmosphere.status().label());
        lines.add("Temperature: " + UnitFormatter.formatTemperature(atmosphere.liveTemperatureC()));
        lines.add("Humidity: " + formatRatio(atmosphere.liveHumidity()));
        lines.add("Pressure: " + UnitFormatter.formatPressure(atmosphere.livePressureHpa()));
        lines.add("Cloud Cover: " + formatRatio(atmosphere.cloudCover()));
        lines.add("Cloud Water: " + formatRatio(atmosphere.cloudWater()));
        lines.add("Rain Intensity: " + formatRatio(atmosphere.rainIntensity()));
        lines.add("Wind Strength: " + formatNumber(atmosphere.windStrength()));
        lines.add("Wind Direction: " + formatDegrees(atmosphere.windDirectionDeg()));
        if (atmosphere.sunlight() != null) {
            lines.add("Sunlight: " + formatNumber(atmosphere.sunlight()));
        }
        if (atmosphere.forecastTemperatureC() != null) {
            lines.add("Forecast Temperature: " + UnitFormatter.formatTemperature(atmosphere.forecastTemperatureC()));
            lines.add("Live Temperature: " + UnitFormatter.formatTemperature(atmosphere.liveTemperatureC()));
            lines.add("Delta: " + formatSignedDelta(atmosphere.liveTemperatureC() - atmosphere.forecastTemperatureC(), "°C"));
        }
        if (atmosphere.baseForecastTemperatureC() != null) {
            lines.add("Base Forecast Temperature: " + UnitFormatter.formatTemperature(atmosphere.baseForecastTemperatureC()));
        }
        if (atmosphere.seasonTemperatureOffsetC() != null) {
            lines.add("Season Temperature Offset: " + formatSignedDelta(atmosphere.seasonTemperatureOffsetC(), " C"));
        }
        if (atmosphere.effectiveForecastTemperatureC() != null) {
            lines.add("Effective Forecast Temperature: " + UnitFormatter.formatTemperature(atmosphere.effectiveForecastTemperatureC()));
        }
        if (atmosphere.deltaToBaseForecastC() != null) {
            lines.add("Delta To Base Forecast: " + formatSignedDelta(atmosphere.deltaToBaseForecastC(), " C"));
        }
        if (atmosphere.deltaToEffectiveForecastC() != null) {
            lines.add("Delta To Effective Forecast: " + formatSignedDelta(atmosphere.deltaToEffectiveForecastC(), " C"));
        }
        if (atmosphere.schedulerTemperatureDeltaC() != null) {
            lines.add("Scheduler Temperature Delta: " + formatSignedDelta(atmosphere.schedulerTemperatureDeltaC(), " C"));
        }
        if (atmosphere.baseRelaxTemperatureDeltaC() != null) {
            lines.add("Base Relax Temperature Delta: " + formatSignedDelta(atmosphere.baseRelaxTemperatureDeltaC(), " C"));
        }
        if (atmosphere.seasonalDriftTemperatureDeltaC() != null) {
            lines.add("Seasonal Drift Temperature Delta: " + formatSignedDelta(atmosphere.seasonalDriftTemperatureDeltaC(), " C"));
        }
        if (atmosphere.forecastHumidity() != null) {
            float forecastHumidityRatio = atmosphere.forecastHumidity() / 100f;
            lines.add("Forecast Humidity: " + formatRatio(forecastHumidityRatio));
            lines.add("Live Humidity: " + formatRatio(atmosphere.liveHumidity()));
            lines.add("Delta: " + formatSignedDelta(atmosphere.liveHumidity() - forecastHumidityRatio, ""));
        }
        if (atmosphere.forecastPressureHpa() != null) {
            lines.add("Forecast Pressure: " + UnitFormatter.formatPressure(atmosphere.forecastPressureHpa()));
            lines.add("Live Pressure: " + UnitFormatter.formatPressure(atmosphere.livePressureHpa()));
            lines.add("Delta: " + formatSignedDelta(atmosphere.livePressureHpa() - atmosphere.forecastPressureHpa(), " hPa"));
        }
        if (atmosphere.pressureTargetHpa() != null) {
            lines.add("Pressure Target: " + UnitFormatter.formatPressure(atmosphere.pressureTargetHpa()));
        }
        if (atmosphere.forecastPressureCurrentSampleHpa() != null) {
            lines.add("Forecast Pressure Current Sample: " + UnitFormatter.formatPressure(atmosphere.forecastPressureCurrentSampleHpa()));
        }
        if (atmosphere.liveStateRawPressureTargetHpa() != null) {
            lines.add("Live State Raw Pressure Target: " + UnitFormatter.formatPressure(atmosphere.liveStateRawPressureTargetHpa()));
        }
        if (atmosphere.effectivePressureTargetHpa() != null) {
            lines.add("Effective Pressure Target: " + UnitFormatter.formatPressure(atmosphere.effectivePressureTargetHpa()));
        }
        if (atmosphere.pressureTargetSource() != null) {
            lines.add("Target Source: " + atmosphere.pressureTargetSource());
        }
        if (atmosphere.pressureTargetDayIndex() != null) {
            lines.add("Target Day Index: " + atmosphere.pressureTargetDayIndex());
        }
        if (atmosphere.currentForecastDayIndex() != null) {
            lines.add("Current Forecast Day Index: " + atmosphere.currentForecastDayIndex());
        }
        if (atmosphere.pressureTargetUsesCurrentForecastDay() != null) {
            lines.add("Target Uses Current Forecast Day: " + (atmosphere.pressureTargetUsesCurrentForecastDay() ? "yes" : "no"));
        }
        if (atmosphere.day0PressureTargetProfileActive() != null) {
            lines.add("Day-0 Target Profile Active: " + (atmosphere.day0PressureTargetProfileActive() ? "yes" : "no"));
        }
        if (atmosphere.stalePressureTargetDetected() != null) {
            lines.add("Stale Target Detected: " + (atmosphere.stalePressureTargetDetected() ? "yes" : "no"));
        }
        if (atmosphere.stalePressureTargetCorrectionDelta() != null) {
            lines.add("Stale Target Correction Delta: " + formatSignedDelta(atmosphere.stalePressureTargetCorrectionDelta(), " hPa"));
        }
        if (atmosphere.pressureAnomalyClassification() != null) {
            lines.add("Pressure Anomaly Classification: " + atmosphere.pressureAnomalyClassification());
        }
        if (atmosphere.normalPressureReferenceHpa() != null) {
            lines.add("Normal Pressure Reference: " + UnitFormatter.formatPressure(atmosphere.normalPressureReferenceHpa()));
        }
        if (atmosphere.pressureDeltaToForecastHpa() != null) {
            lines.add("Pressure Delta To Forecast: " + formatSignedDelta(atmosphere.pressureDeltaToForecastHpa(), " hPa"));
        }
        if (atmosphere.pressureDeltaToNormalHpa() != null) {
            lines.add("Pressure Delta To Normal: " + formatSignedDelta(atmosphere.pressureDeltaToNormalHpa(), " hPa"));
        }
        if (atmosphere.schedulerPressureDelta() != null) {
            lines.add("Scheduler Pressure Delta: " + formatSignedDelta(atmosphere.schedulerPressureDelta(), " hPa"));
        }
        if (atmosphere.forecastRecoveryPressureDelta() != null) {
            lines.add("Recovery Pressure Delta: " + formatSignedDelta(atmosphere.forecastRecoveryPressureDelta(), " hPa"));
        }
        if (atmosphere.pressureGuardDelta() != null) {
            lines.add("Pressure Guard Delta: " + formatSignedDelta(atmosphere.pressureGuardDelta(), " hPa"));
        }
        if (atmosphere.baseRelaxPressureDelta() != null) {
            lines.add("Base Relax Pressure Delta: " + formatSignedDelta(atmosphere.baseRelaxPressureDelta(), " hPa"));
        }
        if (atmosphere.rainPressureDelta() != null) {
            lines.add("Rain Pressure Delta: " + formatSignedDelta(atmosphere.rainPressureDelta(), " hPa"));
        }
        if (atmosphere.windPressureMixDelta() != null) {
            lines.add("Wind Pressure Mix Delta: " + formatSignedDelta(atmosphere.windPressureMixDelta(), " hPa"));
        }
        if (atmosphere.oceanFlux() != null) {
            lines.add("Ocean Flux: " + formatNumber(atmosphere.oceanFlux()));
        }
        if (atmosphere.oceanPressureInfluence() != null) {
            lines.add("Ocean Pressure Influence: " + formatSignedDelta(atmosphere.oceanPressureInfluence(), " hPa"));
        }
        if (atmosphere.cyclonePressureInfluence() != null) {
            lines.add("Cyclone Pressure Influence: " + formatSignedDelta(atmosphere.cyclonePressureInfluence(), " hPa"));
        }
        if (atmosphere.stormPressureSupport() != null) {
            lines.add("Storm Pressure Support: " + formatNumber(atmosphere.stormPressureSupport()));
        }
        if (atmosphere.thunderstormSupport() != null) {
            lines.add("Thunderstorm Support: " + formatNumber(atmosphere.thunderstormSupport()));
        }
        if (atmosphere.seasonPressureOffsetHpa() != null) {
            lines.add("Season Pressure Offset: " + formatSignedDelta(atmosphere.seasonPressureOffsetHpa(), " hPa"));
        }
        if (atmosphere.seasonTemperatureOffsetC() != null) {
            lines.add("Season Temperature Offset: " + formatSignedDelta(atmosphere.seasonTemperatureOffsetC(), "Â°C"));
        }
        if (atmosphere.pressureRecoveryEligible() != null) {
            lines.add("Pressure Recovery Eligible: " + (atmosphere.pressureRecoveryEligible() ? "yes" : "no"));
        }
        if (atmosphere.cycloneSeedEligible() != null) {
            lines.add("Cyclone Seed Eligible: " + (atmosphere.cycloneSeedEligible() ? "yes" : "no"));
        }
        if (atmosphere.cycloneSeedSupport() != null) {
            lines.add("Cyclone Seed Support: " + formatNumber(atmosphere.cycloneSeedSupport()));
        }
        if (atmosphere.cycloneIntensificationSupport() != null) {
            lines.add("Cyclone Intensification Support: " + formatNumber(atmosphere.cycloneIntensificationSupport()));
        }
        if (atmosphere.cycloneSevereSupport() != null) {
            lines.add("Cyclone Severe Support: " + formatNumber(atmosphere.cycloneSevereSupport()));
        }
        if (atmosphere.unsupportedLowRecoveryActive() != null) {
            lines.add("Unsupported Low Recovery Active: " + (atmosphere.unsupportedLowRecoveryActive() ? "yes" : "no"));
        }
        if (atmosphere.unsupportedLowRecoveryDelta() != null) {
            lines.add("Unsupported Low Recovery Delta: " + formatSignedDelta(atmosphere.unsupportedLowRecoveryDelta(), " hPa"));
        }
        if (atmosphere.unsupportedLowRecoveryCapPerDay() != null) {
            lines.add("Unsupported Low Recovery Cap: " + formatNumber(atmosphere.unsupportedLowRecoveryCapPerDay()) + " hPa/day");
        }
        if (atmosphere.supportResistance() != null) {
            lines.add("Support Resistance: " + formatNumber(atmosphere.supportResistance()));
        }
    }

    private static void appendWind(List<String> lines, VerificationReport.WindSection wind) {
        lines.add("Wind:");
        lines.add("Status: " + wind.status().label());
        lines.add("Runtime Exists: " + (wind.runtimeExists() ? "yes" : "no"));
        lines.add("Speed: " + UnitFormatter.formatWindSpeed(wind.speedMps()));
        lines.add("Direction: " + formatDegrees(wind.directionDeg()));
        lines.add("Gust Strength: " + UnitFormatter.formatWindSpeed(wind.gustStrength()));
        lines.add("Gust Modifier: " + formatNumber(wind.gustModifier()));
        lines.add("Gust: " + (wind.gustActive() ? "Active" : "Inactive"));
        lines.add("Persistence: " + (wind.persistencePresent() ? "present" : "missing"));
    }

    private static void appendSeason(List<String> lines, VerificationReport.SeasonSection season) {
        lines.add("Season:");
        lines.add("Status: " + season.status().label());
        lines.add("Provider: " + season.providerId());
        lines.add("Stage: " + season.stage());
        lines.add("Progress: " + formatNumber(season.progress()));
        lines.add("Temperature Offset: " + formatSignedDelta(season.temperatureOffset(), "°C"));
        lines.add("Drift Temperature Offset: " + formatSignedDelta(season.driftTemperatureOffset(), "°C"));
        lines.add("Drift Pressure Offset: " + formatSignedDelta(season.driftPressureOffset(), " hPa"));
        lines.add("Seasonal Drift: " + (season.driftInitialized() ? "active" : "idle"));
        lines.add("Drift Persistence: " + (season.driftPersistencePresent() ? "present" : "missing"));
    }

    private static void appendWeatherCells(List<String> lines, VerificationReport.WeatherCellSection cells) {
        lines.add("WeatherCells:");
        lines.add("Status: " + cells.status().label());
        lines.add("Active: " + cells.totalActive());
        lines.add("Rain: " + cells.rainCellCount());
        lines.add("Thunderstorm: " + cells.thunderstormCount());
        lines.add("Supercell: " + cells.supercellCount());
        VerificationReport.NearestWeatherCell nearest = cells.nearest();
        if (nearest != null) {
            lines.add("Nearest Cell:");
            lines.add("Type: " + nearest.type());
            lines.add("Distance: " + Math.round(nearest.distanceMeters()) + "m");
            lines.add("Intensity: " + formatNumber(nearest.intensity()));
            lines.add("Radius: " + formatNumber(nearest.radius()));
            lines.add("EvolutionScore: " + formatNumber(nearest.evolutionScore()));
            lines.add("SevereEvolutionScore: " + formatNumber(nearest.severeEvolutionScore()));
            lines.add("Region: " + nearest.region());
        }
    }

    private static void appendClouds(List<String> lines, VerificationReport.CloudSection clouds) {
        lines.add("Clouds:");
        lines.add("Status: " + clouds.status().label());
        lines.add("Active Regions: " + clouds.activeRegionCount());
        lines.add("Active Clusters: " + clouds.activeClusterCount());
        VerificationReport.NearestCloud nearest = clouds.nearest();
        if (nearest != null) {
            lines.add("Nearest Cloud:");
            lines.add("Type: " + nearest.cloudTypeId());
            lines.add("Distance: " + Math.round(nearest.distanceMeters()) + "m");
            lines.add("Cloud Cover Contribution: " + formatRatio(nearest.cloudCoverContribution()));
            lines.add("Cloud Water Contribution: " + formatRatio(nearest.cloudWaterContribution()));
        }
    }

    private static void appendCloudBackend(List<String> lines, VerificationReport.CloudBackendSection backend) {
        lines.add("Cloud Backend:");
        lines.add("Current Visual Backend: " + backend.currentVisualBackend());
        lines.add("Last Visual Backend: " + backend.lastVisualBackend());
        lines.add("Simple Clouds Loaded: " + (backend.simpleCloudsLoaded() ? "yes" : "no"));
        lines.add("PA Native Clouds Stored: " + backend.paNativeCloudsStored());
        lines.add("PA Native Clouds Rendered: " + backend.paNativeCloudsRendered());
        lines.add("Bridge Snapshots Stored: " + backend.bridgeSnapshotsStored());
        lines.add("Last Migration Direction: " + backend.lastMigrationDirection());
        lines.add("Duplicate Visual Cloud Risk: " + (backend.duplicateVisualCloudRisk() ? "yes" : "no"));
        lines.add("Migration Status: " + backend.migrationStatus().label());
    }

    private static void appendPaNativeBackend(List<String> lines, VerificationReport.PaNativeBackendSection backend) {
        lines.add("PA-Native Cloud Backend:");
        lines.add("PA Native Enabled: " + (backend.paNativeEnabled() ? "yes" : "no"));
        lines.add("PA Native Clouds Stored: " + backend.paNativeCloudsStored());
        lines.add("PA Native Clouds Synced: " + backend.paNativeCloudsSynced());
        lines.add("PA Native Clouds Rendered: " + backend.paNativeCloudsRendered());
        lines.add("Native Cloud Sync Active: " + (backend.nativeCloudSyncActive() ? "yes" : "no"));
        lines.add("Native Cloud Render Active: " + (backend.nativeCloudRenderActive() ? "yes" : "no"));
        lines.add("Native Cloud Shadow Active: " + (backend.nativeCloudShadowActive() ? "yes" : "no"));
        lines.add("Fallback Darkening Active: " + (backend.fallbackDarkeningActive() ? "yes" : "no"));
        lines.add("Lighting Metadata Active: " + (backend.lightingMetadataActive() ? "yes" : "no"));
        lines.add("DH Metadata Active: " + (backend.dhMetadataActive() ? "yes" : "no"));
    }

    private static void appendMorphology(List<String> lines, VerificationReport.MorphologySection morphology) {
        lines.add("Morphology:");
        lines.add("Status: " + morphology.status().label());
        for (CloudMorphologyFamily family : CloudMorphologyFamily.values()) {
            int count = morphology.countsByFamily().getOrDefault(family, 0);
            lines.add(family.name() + ": " + count);
        }
        if (morphology.missingAssignments() > 0) {
            lines.add("Missing Assignments: " + morphology.missingAssignments());
        }
        if (morphology.unknownAssignments() > 0) {
            lines.add("Unknown Assignments: " + morphology.unknownAssignments());
        }
        if (morphology.fallbackAssignments() > 0) {
            lines.add("Fallback Usage: " + morphology.fallbackAssignments());
        }
    }

    private static void appendEvolution(List<String> lines, VerificationReport.EvolutionSection evolution) {
        lines.add("Cloud Evolution:");
        lines.add("Status: " + evolution.status().label());
        for (Map.Entry<String, Integer> entry : evolution.countsByType().entrySet()) {
            lines.add(entry.getKey() + ": " + entry.getValue());
        }
        VerificationReport.NearestEvolvingCloud nearest = evolution.nearest();
        if (nearest != null) {
            lines.add("Nearest Evolving Cloud:");
            lines.add("Type: " + nearest.cloudTypeId());
            lines.add("Morphology: " + nearest.morphologyFamily());
            lines.add("Density: " + formatNumber(nearest.density()));
            lines.add("Radius: " + formatNumber(nearest.radius()));
            lines.add("Coverage: " + formatRatio(nearest.coverage()));
            lines.add("CloudTypeTicks: " + nearest.cloudTypeTicks());
            lines.add("Previous Type: " + nearest.previousCloudTypeId());
            lines.add("Transition Blend: " + formatNumber(nearest.transitionBlend()));
            lines.add("Raw Radius: " + formatNumber(nearest.rawRadius()));
            lines.add("Rendered Radius: " + formatNumber(nearest.renderedRadius()));
            lines.add("Target Radius: " + formatNumber(nearest.targetRadius()));
            lines.add("Radius Cap: " + nearest.radiusCap());
            lines.add("Radius Delta: " + formatNumber(nearest.radiusDelta()));
            lines.add("Growth Rate: " + formatNumber(nearest.growthRate()));
            lines.add("Growth Blocked Reason: " + nearest.growthBlockedReason());
            lines.add("Raw Coverage: " + formatRatio(nearest.rawCoverage()));
            lines.add("Rendered Coverage: " + formatRatio(nearest.renderedCoverage()));
            lines.add("Target Coverage: " + formatRatio(nearest.targetCoverage()));
            lines.add("Raw Density: " + formatRatio(nearest.rawDensity()));
            lines.add("Rendered Density: " + formatRatio(nearest.renderedDensity()));
            lines.add("Target Density: " + formatRatio(nearest.targetDensity()));
            lines.add("Evolution Support: " + formatRatio(nearest.evolutionSupport()));
            lines.add("Growth Support: " + formatRatio(nearest.growthSupport()));
            lines.add("Growth Phase: " + nearest.growthPhase());
            lines.add("Migration Source: " + nearest.migrationSource());
            lines.add("Bridge Snapshot Id: " + nearest.bridgeSnapshotId());
            lines.add("Cloud World Position: " + nearest.cloudWorldPosition());
            lines.add("Previous Cloud Position: " + nearest.previousCloudPosition());
            lines.add("Cloud Velocity: " + nearest.cloudVelocity());
            lines.add("Cloud Drift Speed: " + formatNumber(nearest.cloudDriftSpeed()));
            lines.add("Cloud Wind Coupling: " + formatNumber(nearest.cloudWindCoupling()));
            lines.add("Cloud Motion Source: " + nearest.cloudMotionSource());
            lines.add("Last Motion Tick: " + nearest.lastMotionTick());
            lines.add("Last Growth Tick: " + nearest.lastGrowthTick());
            lines.add("Last Render Snapshot Tick: " + nearest.lastRenderSnapshotTick());
            lines.add("Shape Seed: " + nearest.shapeSeed());
            lines.add("Morphology Noise Stable: " + (nearest.morphologyNoiseStable() ? "yes" : "no"));
            lines.add("Render Bounds: " + nearest.renderBounds());
            lines.add("LOD Tier: " + nearest.lodTier());
        }
    }

    private static void appendNearestNativeCloud(List<String> lines, VerificationReport.NearestNativeCloud nearest) {
        lines.add("Nearest Native Cloud:");
        if (nearest == null) {
            lines.add("Cloud Id: none");
            return;
        }
        lines.add("Cloud Id: " + nearest.cloudId());
        lines.add("Type: " + nearest.type());
        lines.add("Position: " + nearest.position());
        lines.add("Previous Position: " + nearest.previousPosition());
        lines.add("Velocity: " + nearest.velocity());
        lines.add("Wind Coupling: " + formatNumber(nearest.windCoupling()));
        lines.add("Motion Active: " + (nearest.motionActive() ? "yes" : "no"));
        lines.add("Motion Blocked Reason: " + nearest.motionBlockedReason());
        lines.add("Radius: " + formatNumber(nearest.radius()));
        lines.add("Target Radius: " + formatNumber(nearest.targetRadius()));
        lines.add("Rendered Radius: " + formatNumber(nearest.renderedRadius()));
        lines.add("Radius Cap: " + formatNumber(nearest.radiusCap()));
        lines.add("Coverage: " + formatRatio(nearest.coverage()));
        lines.add("Target Coverage: " + formatRatio(nearest.targetCoverage()));
        lines.add("Density: " + formatRatio(nearest.density()));
        lines.add("Target Density: " + formatRatio(nearest.targetDensity()));
        lines.add("Growth Rate: " + formatNumber(nearest.growthRate()));
        lines.add("Growth Active: " + (nearest.growthActive() ? "yes" : "no"));
        lines.add("Growth Blocked Reason: " + nearest.growthBlockedReason());
        lines.add("Age: " + nearest.age());
        lines.add("Lifetime: " + nearest.lifetime());
        lines.add("Last State Tick: " + nearest.lastStateTick());
        lines.add("Last Sync Tick: " + nearest.lastSyncTick());
        lines.add("Last Render Snapshot Tick: " + nearest.lastRenderSnapshotTick());
        lines.add("Render Bounds: " + nearest.renderBounds());
    }

    private static void appendPersistence(List<String> lines, VerificationReport.PersistenceSection persistence) {
        lines.add("Persistence:");
        lines.add("Status: " + persistence.status().label());
        lines.add("Forecast: " + persistence.forecast().label());
        lines.add("Atmosphere: " + persistence.atmosphere().label());
        lines.add("Seasonal Drift: " + persistence.seasonalDrift().label());
        lines.add("Wind: " + persistence.wind().label());
        lines.add("WeatherCells: " + persistence.weatherCells().label());
        lines.add("Cloud Regions: " + persistence.cloudRegions().label());
    }

    private static String formatNumber(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void addNullable(List<String> lines, String key, Float value) {
        if (value != null) {
            lines.add(key + "=" + formatNumber(value));
        }
    }

    private static void addNullable(List<String> lines, String key, Integer value) {
        if (value != null) {
            lines.add(key + "=" + value);
        }
    }

    private static void addNullable(List<String> lines, String key, Boolean value) {
        if (value != null) {
            lines.add(key + "=" + value);
        }
    }

    private static void addNullable(List<String> lines, String key, String value) {
        if (value != null) {
            lines.add(key + "=" + value);
        }
    }

    private static String formatRatio(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatDegrees(float degrees) {
        return String.format(Locale.ROOT, "%.0f°", degrees);
    }

    private static String formatSignedDelta(float delta, String suffix) {
        String sign = delta >= 0f ? "+" : "";
        return sign + String.format(Locale.ROOT, "%.1f", delta) + suffix;
    }
}

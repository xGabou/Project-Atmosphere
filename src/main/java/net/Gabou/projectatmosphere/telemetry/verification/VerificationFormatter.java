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
        appendMorphology(lines, report.morphology());
        lines.add("");
        appendEvolution(lines, report.evolution());
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
        lines.add("atmosphere.humidity=" + formatNumber(atmosphere.liveHumidity()));
        lines.add("atmosphere.pressure=" + formatNumber(atmosphere.livePressureHpa()));
        lines.add("atmosphere.cloudWater=" + formatNumber(atmosphere.cloudWater()));
        lines.add("atmosphere.cloudCover=" + formatNumber(atmosphere.cloudCover()));
        lines.add("atmosphere.rain=" + formatNumber(atmosphere.rainIntensity()));
        lines.add("atmosphere.windStrength=" + formatNumber(atmosphere.windStrength()));
        lines.add("atmosphere.windDir=" + formatNumber(atmosphere.windDirectionDeg()));
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
        lines.add("season.driftInitialized=" + season.driftInitialized());
        lines.add("weatherCells.active=" + cells.totalActive());
        lines.add("weatherCells.rain=" + cells.rainCellCount());
        lines.add("weatherCells.thunder=" + cells.thunderstormCount());
        lines.add("weatherCells.supercell=" + cells.supercellCount());
        lines.add("clouds.regions=" + clouds.activeRegionCount());
        lines.add("clouds.clusters=" + clouds.activeClusterCount());
        for (CloudMorphologyFamily family : CloudMorphologyFamily.values()) {
            int count = report.morphology().countsByFamily().getOrDefault(family, 0);
            lines.add("morphology." + family.name() + "=" + count);
        }
        for (Map.Entry<String, Integer> entry : report.evolution().countsByType().entrySet()) {
            lines.add("evolution." + entry.getKey() + "=" + entry.getValue());
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
        }
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

package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.telemetry.TelemetryCollector;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.AnomalyMarker;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.AtmosphereCouplingSample;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.HumidityBudgetSample;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class AtmosphericTelemetryReporter {
    private static final float TEMPERATURE_GUARD_THRESHOLD_C = 6f;
    private static final float PRESSURE_GUARD_THRESHOLD_HPA = 8f;
    private static final Set<String> REPORTED_ANOMALIES = ConcurrentHashMap.newKeySet();

    private AtmosphericTelemetryReporter() {
    }

    static void recordFor(RegionAtmosphereState state,
                          AtmosphericUpdateScheduler.StateDelta delta,
                          float temperatureBefore,
                          float pressureBefore,
                          float humidityBefore,
                          float cloudWaterBefore,
                          long dayTime,
                          String dimensionId,
                          AtmosphericUpdateScheduler.UpdateMode mode) {
        if (!AtmoCommonConfig.TELEMETRY_ENABLED.get()) {
            return;
        }
        if (mode == AtmosphericUpdateScheduler.UpdateMode.ACTIVE) {
            recordAtmosphereCoupling(state, delta, temperatureBefore, pressureBefore, humidityBefore, dayTime, dimensionId, mode);
            recordHumidityBudget(state, delta, humidityBefore, cloudWaterBefore, dayTime, dimensionId, mode);
        }
        recordAnomalies(state, dayTime);
    }

    static void recordAtmosphereCoupling(RegionAtmosphereState state,
                                         AtmosphericUpdateScheduler.StateDelta delta,
                                         float temperatureBefore,
                                         float pressureBefore,
                                         float humidityBefore,
                                         long dayTime,
                                         String dimensionId,
                                         AtmosphericUpdateScheduler.UpdateMode mode) {
        RegionInstanceKey key = state.getRegionId();
        if (key == null) {
            return;
        }
        TelemetryCollector.get().recordAtmosphereCouplingSample(new AtmosphereCouplingSample(
                dayTime / 24000L,
                Math.floorMod(dayTime, 24000L),
                dimensionId,
                key.toString(),
                key.regionX(),
                key.regionZ(),
                key.regionSize(),
                delta.dominantBiomeId(),
                mode.name().toLowerCase(Locale.ROOT),
                delta.targetTemperature(),
                delta.targetPressure(),
                delta.targetHumidity(),
                temperatureBefore,
                state.getTemperature(),
                pressureBefore,
                state.getPressure(),
                humidityBefore,
                state.getHumidity(),
                state.getCloudCover(),
                state.getRainIntensity(),
                delta.temperatureDelta(),
                delta.pressureDelta()
        ));
    }

    static void recordHumidityBudget(RegionAtmosphereState state,
                                     AtmosphericUpdateScheduler.StateDelta delta,
                                     float humidityBefore,
                                     float cloudWaterBefore,
                                     long dayTime,
                                     String dimensionId,
                                     AtmosphericUpdateScheduler.UpdateMode mode) {
        RegionInstanceKey key = state.getRegionId();
        if (key == null) {
            return;
        }
        HumidityBudget budget = delta.humidityBudget();
        CloudWaterExchange exchange = delta.cloudWaterExchange();
        TelemetryCollector.get().recordHumidityBudgetSample(new HumidityBudgetSample(
                dayTime / 24000L,
                Math.floorMod(dayTime, 24000L),
                dimensionId,
                key.toString(),
                key.regionX(),
                key.regionZ(),
                key.regionSize(),
                delta.dominantBiomeId(),
                mode.name().toLowerCase(Locale.ROOT),
                delta.targetHumidity(),
                humidityBefore,
                state.getHumidity(),
                cloudWaterBefore,
                state.getCloudWater(),
                state.getCloudCover(),
                state.getRainIntensity(),
                budget.solarDrying(),
                budget.biomeEvaporation(),
                budget.oceanFlux(),
                budget.rainExchange(),
                budget.windTransport(),
                budget.forecastRestore(),
                budget.precipitationSink(),
                exchange.condensation(),
                exchange.reEvaporation(),
                exchange.precipitationDraw(),
                delta.humidityDelta()
        ));
    }

    static void recordAnomalies(RegionAtmosphereState state, long dayTime) {
        float temperature = state.getTemperature();
        String temperatureKey = state.getRegionId() + ":temperature_outlier";
        if ((temperature < -80f || temperature > 80f) && REPORTED_ANOMALIES.add(temperatureKey)) {
            TelemetryCollector.get().recordAnomaly(new AnomalyMarker(
                    Instant.now(),
                    "temperature_outlier",
                    state.getRegionId().toString(),
                    Map.of("temperature", temperature)
            ));
        }

        float targetTemperature = state.getTargetTemperature(dayTime);
        float temperatureDeviation = Math.abs(targetTemperature - state.getTemperature());
        boolean hiddenTemperatureDrift = temperatureDeviation > TEMPERATURE_GUARD_THRESHOLD_C
                && state.getCloudCover() < 0.1f
                && state.getRainIntensity() < 0.05f;
        String driftKey = state.getRegionId() + ":temperature_drift_from_target";
        if (hiddenTemperatureDrift && REPORTED_ANOMALIES.add(driftKey)) {
            TelemetryCollector.get().recordAnomaly(new AnomalyMarker(
                    Instant.now(),
                    "temperature_drift_from_target",
                    state.getRegionId().toString(),
                    Map.of(
                            "temperature", state.getTemperature(),
                            "targetTemperature", targetTemperature,
                            "temperatureDeviation", temperatureDeviation,
                            "cloudCover", state.getCloudCover(),
                            "rainIntensity", state.getRainIntensity()
                    )
            ));
        }

        float targetPressure = state.getTargetPressure(dayTime);
        float pressureDeviation = Math.abs(targetPressure - state.getPressure());
        boolean hiddenPressureDrift = pressureDeviation > PRESSURE_GUARD_THRESHOLD_HPA
                && state.getCloudCover() < 0.05f
                && state.getRainIntensity() < 0.02f;
        String pressureKey = state.getRegionId() + ":pressure_drift_no_visible_weather";
        if (hiddenPressureDrift && REPORTED_ANOMALIES.add(pressureKey)) {
            TelemetryCollector.get().recordAnomaly(new AnomalyMarker(
                    Instant.now(),
                    "pressure_drift_no_visible_weather",
                    state.getRegionId().toString(),
                    Map.of(
                            "pressure", state.getPressure(),
                            "targetPressure", targetPressure,
                            "pressureDeviation", pressureDeviation,
                            "cloudCover", state.getCloudCover(),
                            "rainIntensity", state.getRainIntensity()
                    )
            ));
        }
    }
}

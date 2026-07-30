package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.api.ForecastSampling;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.data.TornadoStorageManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.weather.RegionalWeatherPhase;
import net.Gabou.projectatmosphere.modules.weather.StormSeverityScale;
import net.Gabou.projectatmosphere.modules.tornado.scheduling.TornadoSpawnScheduler;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;


public final class TornadoProbabilityManager {
    private TornadoProbabilityManager() {}

    public static void init() {
        // No-op for now
    }

    public static void onScheduledCheck(ServerLevel level) {
        if (!AtmoCommonConfig.ENABLE_TORNADOES.get()) return;
        long now = level.getGameTime();
        if (!TornadoSpawnScheduler.isSlotAvailable(now)) return;
        RandomSource random = RandomSource.create();
        for (RegionInstanceKey key : ForecastOrchestrator.getActiveRegions(level)) {
            if (!isStormy(key, level)) continue;
            if (isCellOnCooldown(key, level, now)) continue;

            float risk = computeRisk(key, level, now);
            int stormLevel = StormSeverityScale.resolve(level, key, now);
            float riskMin = AtmoCommonConfig.TORNADO_RISK_MIN_TO_CONSIDER.get().floatValue();
            if (risk < riskMin) continue;
            float chance = AtmoCommonConfig.TORNADO_BASE_TRIGGER_CHANCE.get().floatValue() * risk * (0.55F + StormSeverityScale.toNormalized(stormLevel) * 0.75F);
            if (random.nextFloat() < chance) {
                float intensity = map(risk,
                        riskMin,
                        riskMin + 4f,
                        AtmoCommonConfig.TORNADO_INTENSITY_MIN.get().floatValue(),
                        AtmoCommonConfig.TORNADO_INTENSITY_MAX.get().floatValue());
                intensity = Math.max(intensity, 0.18F + StormSeverityScale.toNormalized(stormLevel) * 0.52F);
                if (!TornadoSpawner.spawn(key, level, clamp01(intensity))) {
                    continue;
                }
                TornadoStorageManager.setCooldown(key,
                        now + minutesToTicks(AtmoCommonConfig.TORNADO_CELL_COOLDOWN_MINUTES.get()));
                TornadoSpawnScheduler.recordSpawn(now);
                break;
            }
        }
    }

    public static float computeRisk(RegionInstanceKey key, ServerLevel level, long nowTick) {
        float risk = 0f;
        risk += thermalRisk(key, level);
        risk += pressureRisk(key, level);
        risk += shearRisk(key, level);
        if (isStormy(key, level)) {
            risk *= AtmoCommonConfig.TORNADO_STORM_MULTIPLIER.get().floatValue();
        }
        return risk;
    }


    public static boolean isCellOnCooldown(RegionInstanceKey key, ServerLevel level, long nowTick) {
        return TornadoStorageManager.isOnCooldown(key, nowTick);
    }


    private static boolean isStormy(RegionInstanceKey key, ServerLevel level) {
        RegionalWeatherPhase phase = ForecastOrchestrator.getWeatherPhase(level, key, level.getGameTime());
        if (!phase.isStormCapable()) {
            return false;
        }
        if (StormSeverityScale.resolve(level, key, level.getGameTime()) < 6) {
            return false;
        }
        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return AtmosphereCloudServices.get().hasSevereCloudNearby(level, key.center(), 7);
        }
        return net.Gabou.projectatmosphere.clouds.cell.sim.CloudCellSimulationManager.getInstance()
                .hasEligibleNativeTornadoCellNear(level, key.center(), 1600.0D);
    }

    private static float thermalRisk(RegionInstanceKey key, ServerLevel level) {
        float tempSurface = ForecastSampling.getTemperatureC(key, level.getGameTime());
        float humidity = ForecastSampling.getHumidityPercent(key, level.getGameTime());
        float tempAloft = tempSurface
                - (AtmoCommonConfig.TORNADO_LAPSE_RATE_C_PER_100M.get().floatValue()
                * (AtmoCommonConfig.TORNADO_ALOFT_DELTA_H_M.get().floatValue() / 100f));
        float tempContrast = Math.max(0f, tempSurface - tempAloft);
        if (tempContrast < AtmoCommonConfig.TORNADO_MIN_TEMP_CONTRAST_C.get().floatValue()) {
            return 0.0F;
        }
        float risk = tempContrast / 10f;
        if (humidity >= AtmoCommonConfig.TORNADO_HUMIDITY_MIN_PERCENT.get().floatValue()) {
            risk += 1f;
        }
        return risk;
    }

    private static float pressureRisk(RegionInstanceKey key, ServerLevel level) {
        float pHere = ForecastSampling.getPressureHpa(key, level.getGameTime());
        float pNear = ForecastSampling.minNeighborPressureHpa(key, level.getGameTime());
        float pDiff = Math.abs(pHere - pNear) * AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_GAIN.get().floatValue();
        return Math.min(pDiff, AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_CAP.get().floatValue());
    }

    private static float shearRisk(RegionInstanceKey key, ServerLevel level) {
        WindVectorApi.WindSample wSurf = WindVectorApi.getOrFallback(key, level.getGameTime());
        WindVectorApi.WindSample wAloft = WindVectorApi.getAloftProxy(key, level);
        float speedDiff = Math.abs(wSurf.speedMps() - wAloft.speedMps());
        float dirDiff = minimalAngleDiffDeg(wSurf.directionDeg(), wAloft.directionDeg());
        if (speedDiff >= AtmoCommonConfig.TORNADO_SHEAR_MIN_SPEED_DIFF_MPS.get().floatValue()
                && dirDiff >= AtmoCommonConfig.TORNADO_SHEAR_MIN_DIR_DIFF_DEG.get().floatValue()) {
            return 2f;
        }
        return 0f;
    }

    private static float minimalAngleDiffDeg(float a, float b) {
        float d = Math.abs(((a - b + 540f) % 360f) - 180f);
        return d;
    }

    private static long minutesToTicks(float m) {
        return (long) (m * 20f * 60f);
    }

    private static float map(float v, float inMin, float inMax, float outMin, float outMax) {
        float t = (v - inMin) / Math.max(0.0001f, (inMax - inMin));
        return outMin + (clamp01(t) * (outMax - outMin));
    }

    private static float clamp01(float x) {
        return Math.max(0f, Math.min(1f, x));
    }
}


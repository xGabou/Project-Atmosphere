package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.api.ForecastSampling;
import net.Gabou.projectatmosphere.api.WindVector;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.data.TornadoStorageManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public final class TornadoProbabilityManager {
    private TornadoProbabilityManager() {}

    public static void init() {
        // No-op for now
    }

    public static void onScheduledCheck(ServerLevel level) {
        if (!AtmoCommonConfig.ENABLE_TORNADOES.get()) return;
        long now = level.getGameTime();
        for (BiomeInstanceKey key : ForecastOrchestrator.getActiveBiomeKeys(level)) {//TODO fill this first
            if (!isStormy(key, level)) continue;
            if (isCellOnCooldown(key, level, now)) continue;

            float risk = computeRisk(key, level, now);
            float riskMin = AtmoCommonConfig.TORNADO_RISK_MIN_TO_CONSIDER.get().floatValue();
            if (risk < riskMin) continue;

            float chance = AtmoCommonConfig.TORNADO_BASE_TRIGGER_CHANCE.get().floatValue() * risk;
            if (level.random.nextFloat() < chance) {
                float intensity = map(risk,
                        riskMin,
                        riskMin + 4f,
                        AtmoCommonConfig.TORNADO_INTENSITY_MIN.get().floatValue(),
                        AtmoCommonConfig.TORNADO_INTENSITY_MAX.get().floatValue());
                TornadoSpawner.spawn(key, level, clamp01(intensity));
                TornadoStorageManager.setCooldown(key,
                        now + minutesToTicks(AtmoCommonConfig.TORNADO_CELL_COOLDOWN_MINUTES.get()));
            }
        }
    }

    public static float computeRisk(BiomeInstanceKey key, ServerLevel level, long nowTick) {
        float risk = 0f;

        float tempSurface = ForecastSampling.getTemperatureC(key, level);
        float humidity = ForecastSampling.getHumidityPercent(key, level);
        float tempAloft = tempSurface -
                (AtmoCommonConfig.TORNADO_LAPSE_RATE_C_PER_100M.get().floatValue() *
                        (AtmoCommonConfig.TORNADO_ALOFT_DELTA_H_M.get().floatValue() / 100f));
        float tempContrast = Math.max(0f, tempSurface - tempAloft);
        risk += (tempContrast / 10f);
        if (humidity >= AtmoCommonConfig.TORNADO_HUMIDITY_MIN_PERCENT.get().floatValue()) risk += 1f;

        float pHere = ForecastSampling.getPressureHpa(key, level);
        float pNear = ForecastSampling.minNeighborPressureHpa(key, level);
        float pDiff = Math.abs(pHere - pNear) * AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_GAIN.get().floatValue();
        risk += Math.min(pDiff,
                AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_CAP.get().floatValue());

        WindVector.WindSample wSurf = WindVector.getOrFallback(key, level);
        WindVector.WindSample wAloft = WindVector.getAloftProxy(key, level);
        float speedDiff = Math.abs(wSurf.speedMps() - wAloft.speedMps());
        float dirDiff = minimalAngleDiffDeg(wSurf.directionDeg(), wAloft.directionDeg());
        if (speedDiff >= AtmoCommonConfig.TORNADO_SHEAR_MIN_SPEED_DIFF_MPS.get().floatValue() &&
                dirDiff >= AtmoCommonConfig.TORNADO_SHEAR_MIN_DIR_DIFF_DEG.get().floatValue()) {
            risk += 2f;
        }

        if (isStormy(key, level))
            risk *= AtmoCommonConfig.TORNADO_STORM_MULTIPLIER.get().floatValue();

        return risk;
    }

    public static boolean isCellOnCooldown(BiomeInstanceKey key, ServerLevel level, long nowTick) {
        return TornadoStorageManager.isOnCooldown(key, nowTick);
    }

    private static boolean isStormy(BiomeInstanceKey key, ServerLevel level) {
        return ForecastSampling.isStormyClouds(key, level);
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


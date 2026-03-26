package net.Gabou.projectatmosphere.modules.tornado;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.api.ForecastSampling;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.data.TornadoStorageManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.weather.RegionalWeatherPhase;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
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
            float riskMin = AtmoCommonConfig.TORNADO_RISK_MIN_TO_CONSIDER.get().floatValue();
            if (risk < riskMin) continue;
            float chance = AtmoCommonConfig.TORNADO_BASE_TRIGGER_CHANCE.get().floatValue() * risk;
            if (random.nextFloat() < chance) {
                float intensity = map(risk,
                        riskMin,
                        riskMin + 4f,
                        AtmoCommonConfig.TORNADO_INTENSITY_MIN.get().floatValue(),
                        AtmoCommonConfig.TORNADO_INTENSITY_MAX.get().floatValue());
                TornadoSpawner.spawn(key, level, clamp01(intensity));
                TornadoStorageManager.setCooldown(key,
                        now + minutesToTicks(AtmoCommonConfig.TORNADO_CELL_COOLDOWN_MINUTES.get()));
                TornadoSpawnScheduler.recordSpawn(now);
                break;
            }
        }
    }

    public static float computeRisk(RegionInstanceKey key, ServerLevel level, long nowTick) {
        float risk = 0f;

        float tempSurface = ForecastSampling.getTemperatureC(key, level.getGameTime());
        float humidity = ForecastSampling.getHumidityPercent(key, level.getGameTime());
        float tempAloft = tempSurface -
                (AtmoCommonConfig.TORNADO_LAPSE_RATE_C_PER_100M.get().floatValue() *
                        (AtmoCommonConfig.TORNADO_ALOFT_DELTA_H_M.get().floatValue() / 100f));
        float tempContrast = Math.max(0f, tempSurface - tempAloft);
        risk += (tempContrast / 10f);
        if (humidity >= AtmoCommonConfig.TORNADO_HUMIDITY_MIN_PERCENT.get().floatValue()) risk += 1f;

        float pHere = ForecastSampling.getPressureHpa(key, level.getGameTime());
        float pNear = ForecastSampling.minNeighborPressureHpa(key, level.getGameTime());
        float pDiff = Math.abs(pHere - pNear) * AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_GAIN.get().floatValue();
        risk += Math.min(pDiff,
                AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_CAP.get().floatValue());

        WindVectorApi.WindSample wSurf = WindVectorApi.getOrFallback(key, level.getGameTime());
        WindVectorApi.WindSample wAloft = WindVectorApi.getAloftProxy(key, level);
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

    @Deprecated
    public static float computeRisk(BiomeInstanceKey key, ServerLevel level, long nowTick) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey == null) {
            return 0f;
        }
        return computeRisk(regionKey, level, nowTick);
    }

    public static boolean isCellOnCooldown(RegionInstanceKey key, ServerLevel level, long nowTick) {
        return TornadoStorageManager.isOnCooldown(key, nowTick);
    }

    @Deprecated
    public static boolean isCellOnCooldown(BiomeInstanceKey key, ServerLevel level, long nowTick) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey == null) {
            return false;
        }
        return isCellOnCooldown(regionKey, level, nowTick);
    }

    private static boolean isStormy(RegionInstanceKey key, ServerLevel level) {
        RegionalWeatherPhase phase = ForecastOrchestrator.getWeatherPhase(level, key, level.getGameTime());
        if (!phase.isStormCapable()) {
            return false;
        }
        ServerCloudManager manager = (ServerCloudManager) CloudManager.get(level);
        CloudGenerator generator = manager.getCloudGenerator();
        BlockPos pos = key.center();
        for (CloudRegion region : generator.getClouds()) {
            int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
            if (severity < 7) continue;
            double dx = region.getWorldX() - pos.getX();
            double dz = region.getWorldZ() - pos.getZ();
            double r = region.getRadius();
            if (dx * dx + dz * dz <= r * r) {
                return true;
            }
        }
        return false;
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


package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ForecastSampling {
    private ForecastSampling() {}

    @Deprecated
    public static float getTemperatureC(BiomeInstanceKey key, ServerLevel level) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey != null) {
            return getTemperatureC(regionKey, level.getGameTime());
        }
        return ForecastOrchestrator.getCurrentTemperature(key, level.getGameTime());
    }

    public static float getTemperatureC(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentTemperature(level, pos, level.getGameTime());
    }

    public static float getTemperatureC(RegionInstanceKey regionKey, long tick) {
        return ForecastOrchestrator.getCurrentTemperature(regionKey, tick);
    }

    @Deprecated
    public static float getHumidityPercent(BiomeInstanceKey key, ServerLevel level) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey != null) {
            return getHumidityPercent(regionKey, level.getGameTime());
        }
        return ForecastOrchestrator.getCurrentHumidity(key, level.getGameTime());
    }

    public static float getHumidityPercent(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentHumidity(level, pos, level.getGameTime());
    }

    public static float getHumidityPercent(RegionInstanceKey regionKey, long tick) {
        return ForecastOrchestrator.getCurrentHumidity(regionKey, tick);
    }

    @Deprecated
    public static float getPressureHpa(BiomeInstanceKey key, ServerLevel level) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey != null) {
            return getPressureHpa(regionKey, level.getGameTime());
        }
        return ForecastOrchestrator.getCurrentPressure(key, level.getGameTime());
    }

    public static float getPressureHpa(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentPressure(level, pos, level.getGameTime());
    }

    public static float getPressureHpa(RegionInstanceKey regionKey, long tick) {
        return ForecastOrchestrator.getCurrentPressure(regionKey, tick);
    }

    public static float minNeighborPressureHpa(BiomeInstanceKey key, ServerLevel level) {
        BlockPos base = key.samplePos();
        if (base == null) {
            return getPressureHpa(key, level);
        }
        return minNeighborPressureHpa(level, base);
    }

    public static float minNeighborPressureHpa(ServerLevel level, BlockPos pos) {
        float min = Float.MAX_VALUE;
        int[] dx = {16, -16, 0, 0};
        int[] dz = {0, 0, 16, -16};
        for (int i = 0; i < 4; i++) {
            BlockPos sample = pos.offset(dx[i], 0, dz[i]);
            float p = getPressureHpa(level, sample);
            if (p < min) min = p;
        }
        return min == Float.MAX_VALUE ? getPressureHpa(level, pos) : min;
    }
}


package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ForecastSampling {
    private ForecastSampling() {}

    @Deprecated
    public static float getTemperatureC(BiomeInstanceKey key, ServerLevel level) {
        return ForecastOrchestrator.getCurrentTemperature(key, level.getGameTime());
    }

    public static float getTemperatureC(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentTemperature(level, pos, level.getGameTime());
    }

    @Deprecated
    public static float getHumidityPercent(BiomeInstanceKey key, ServerLevel level) {
        return ForecastOrchestrator.getCurrentHumidity(key, level.getGameTime());
    }

    public static float getHumidityPercent(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentHumidity(level, pos, level.getGameTime());
    }

    @Deprecated
    public static float getPressureHpa(BiomeInstanceKey key, ServerLevel level) {
        return ForecastOrchestrator.getCurrentPressure(key, level.getGameTime());
    }

    public static float getPressureHpa(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentPressure(level, pos, level.getGameTime());
    }

    public static float minNeighborPressureHpa(BiomeInstanceKey key, ServerLevel level) {
        BlockPos base = key.samplePos();
        float min = Float.MAX_VALUE;
        int[] dx = {16, -16, 0, 0};
        int[] dz = {0, 0, 16, -16};
        for (int i = 0; i < 4; i++) {
            BlockPos pos = base.offset(dx[i], 0, dz[i]);
            BiomeInstanceKey neighbor = AtmosphereUtils.getBiomeKey(level, pos);
            float p = getPressureHpa(neighbor, level);
            if (p < min) min = p;
        }
        return min == Float.MAX_VALUE ? getPressureHpa(key, level) : min;
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


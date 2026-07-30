package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.temperature.util.LocalBiomeTemperatureResolver;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ForecastSampling {
    private ForecastSampling() {
    }

    public static Celsius isColdEnoughForSnow(ServerLevel level, BlockPos pos) {
        return LocalBiomeTemperatureResolver.canAccumulateSnow(
                level,
                pos,
                RegionInstanceKey.from(pos),
                ForecastOrchestrator.getRegionForecast(level, pos)
        );
    }

    public static float getTemperatureC(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentTemperature(level, pos, level.getGameTime());
    }

    public static float getTemperatureC(RegionInstanceKey regionKey, long tick) {
        return ForecastOrchestrator.getCurrentTemperature(regionKey, tick);
    }

    public static float getHumidityPercent(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentHumidity(level, pos, level.getGameTime());
    }

    public static float getHumidityPercent(RegionInstanceKey regionKey, long tick) {
        return ForecastOrchestrator.getCurrentHumidity(regionKey, tick);
    }

    public static float getPressureHpa(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentPressure(level, pos, level.getGameTime());
    }

    public static float getPressureHpa(RegionInstanceKey regionKey, long tick) {
        return ForecastOrchestrator.getCurrentPressure(regionKey, tick);
    }

    public static float minNeighborPressureHpa(ServerLevel level, BlockPos pos) {
        float min = Float.MAX_VALUE;
        int[] dx = {16, -16, 0, 0};
        int[] dz = {0, 0, 16, -16};
        for (int i = 0; i < 4; i++) {
            float pressure = getPressureHpa(level, pos.offset(dx[i], 0, dz[i]));
            min = Math.min(min, pressure);
        }
        return min == Float.MAX_VALUE ? getPressureHpa(level, pos) : min;
    }

    public static float minNeighborPressureHpa(RegionInstanceKey regionKey, long tick) {
        float min = Float.MAX_VALUE;
        for (RegionInstanceKey neighbor : AtmosphericStateRegistry.getNeighbors(regionKey)) {
            min = Math.min(min, getPressureHpa(neighbor, tick));
        }
        return min == Float.MAX_VALUE ? getPressureHpa(regionKey, tick) : min;
    }
}

package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Small read-only contract for forecast sampling at different resolution levels.
 * Callers should use these helpers instead of reaching into orchestrator state directly.
 */
public final class ForecastSampling {
    private ForecastSampling() {}

    // ---------------------------------------------------------------------
    // Temperature
    // ---------------------------------------------------------------------

    public static float getTemperatureC(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentTemperature(level, pos, level.getGameTime());
    }

    public static float getTemperatureC(RegionInstanceKey regionKey, long tick) {
        return ForecastOrchestrator.getCurrentTemperature(regionKey, tick);
    }

    // ---------------------------------------------------------------------
    // Humidity
    // ---------------------------------------------------------------------

    public static float getHumidityPercent(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentHumidity(level, pos, level.getGameTime());
    }

    public static float getHumidityPercent(RegionInstanceKey regionKey, long tick) {
        return ForecastOrchestrator.getCurrentHumidity(regionKey, tick);
    }

    // ---------------------------------------------------------------------
    // Pressure
    // ---------------------------------------------------------------------

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
            BlockPos sample = pos.offset(dx[i], 0, dz[i]);
            float p = getPressureHpa(level, sample);
            if (p < min) min = p;
        }
        return min == Float.MAX_VALUE ? getPressureHpa(level, pos) : min;
    }

    public static float minNeighborPressureHpa(RegionInstanceKey regionKey, long tick) {
        float min = Float.MAX_VALUE;
        for (RegionInstanceKey neighbor : AtmosphericStateRegistry.getNeighbors(regionKey)) {
            float pressure = getPressureHpa(neighbor, tick);
            if (pressure < min) {
                min = pressure;
            }
        }
        return min == Float.MAX_VALUE ? getPressureHpa(regionKey, tick) : min;
    }
}


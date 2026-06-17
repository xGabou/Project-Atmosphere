package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

public class PaClimateVigorProvider implements SeasonalTreesVigorProvider {
    @Override
    public float getVigorMultiplier(ServerLevel level, BlockPos pos) {
        long tick = level.getGameTime();
        float temp = ForecastOrchestrator.getCurrentTemperature(level, pos, tick);
        float humidity = ForecastOrchestrator.getCurrentHumidity(level, pos, tick);
        float tempFactor = 1.0f - (Math.abs(temp - 15.0f) / 35.0f);
        float humidityFactor = 0.7f + (humidity * 0.6f);
        return Mth.clamp(tempFactor * humidityFactor, 0.4f, 1.4f);
    }
}

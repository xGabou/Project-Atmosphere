package net.Gabou.projectatmosphere.modules.temperature.command;

import java.util.Map;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.seasons.SeasonSnapshot;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class TemperatureCommandHelper {
    public static RegionInstanceKey getCurrentRegion(Player player) {
        return RegionInstanceKey.from(player.blockPosition());
    }

    public static boolean isInOverworld(Level level) {
        return level.dimension().equals(Level.OVERWORLD);
    }

    public static long getCurrentTick(ServerLevel level) {
        return level.getDayTime() % 24000L;
    }

    public static float getTemperatureAt(ServerLevel level, BlockPos pos, long tick) {
        return ForecastOrchestrator.getCurrentTemperature(level, pos, tick);
    }

    public static String getWeeklyForecast(ServerLevel level, BlockPos pos) {
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null) {
            return "No region forecast is available at this position.";
        }
        return weekForecastToString(region.getKey().toString(), region.getTemperature());
    }

    public static float[] getDayProfile(RegionInstanceKey region) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(region);
        if (state == null) {
            return new float[0];
        }
        return state.getDailyTemperatureProfile();
    }

    public static String getCurrentSubSeason(ServerLevel level) {
        SeasonSnapshot snapshot = SeasonTimeHelper.snapshot(level);
        return snapshot.stage() + " (" + snapshot.providerId() + ", progress "
                + Math.round(snapshot.progress() * 100.0f) + "%)";
    }

    public static float getRealTemperature(ServerLevel level, BlockPos pos) {
        return ForecastOrchestrator.getCurrentTemperature(level, pos, level.getDayTime());
    }

    public static String formatForecastMap(Map<ResourceLocation, float[][]> forecastMap) {
        StringBuilder sb = new StringBuilder("[Temperature Forecast per Biome]");
        for (Map.Entry<ResourceLocation, float[][]> entry : forecastMap.entrySet()) {
            sb.append(weekForecastToString(entry.getKey().toString(), entry.getValue()));
        }
        return sb.toString();
    }

    private static String weekForecastToString(String label, float[][] week) {
        if (week == null || week.length == 0) {
            return label + ": no forecast data";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(label).append(":");
        for (int day = 0; day < week.length; day++) {
            sb.append("\n  Day ").append(day + 1).append(": ");
            float[] profile = week[day];
            if (profile == null || profile.length == 0) {
                sb.append("no data");
                continue;
            }
            for (int i = 0; i < profile.length; i++) {
                sb.append(net.Gabou.projectatmosphere.util.UnitFormatter.formatTemperature(profile[i]));
                if (i < profile.length - 1) sb.append(", ");
            }
        }
        return sb.toString();
    }
}

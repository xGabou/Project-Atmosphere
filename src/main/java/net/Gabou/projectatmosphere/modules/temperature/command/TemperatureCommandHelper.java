package net.Gabou.projectatmosphere.modules.temperature.command;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.seasons.SeasonSnapshot;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;

public class TemperatureCommandHelper {

    public static BiomeInstanceKey getCurrentBiome(Player player) {
        return new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(player.blockPosition(), player.level()), player.blockPosition());
    }

    public static ResourceLocation getCurrentBiomeResourceLocation(Player player) {
        return AtmosphereUtils.getBiomeLocation(player.blockPosition(), player.level());
    }

    public static BiomeInstanceKey resolveBiome(Player player, String biomeStr) {
        if (biomeStr.equalsIgnoreCase("current") || biomeStr.equalsIgnoreCase("currentbiome")) {
            return getCurrentBiome(player);
        }
        try {
            return new BiomeInstanceKey(ResourceLocation.parse(biomeStr), player.blockPosition());
        } catch (Exception e) {
            ProjectAtmosphere.LOGGER.error("Invalid biome name: {}", biomeStr);
            return null;
        }
    }

    public static long getCurrentTick(ServerLevel level) {
        return level.getDayTime() % 24000L;
    }

    public static float getTemperatureAt(BiomeInstanceKey biome, long tick) {
        return ForecastOrchestrator.getCurrentTemperature(biome, tick);
    }

    public static String getWeeklyForecast(ResourceLocation biome) {
        return weekForecastToString(biome, ForecastGenerator.getAverageForecast(biome).getTemperature());
    }

    public static float[] getDayProfile(BiomeInstanceKey biome) {
        var state = AtmosphericStateRegistry.getState(biome);
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

    public static float getRealTemperature(ServerLevel level, BiomeInstanceKey biome, BlockPos pos) {
        return ForecastOrchestrator.getCurrentTemperature(biome, level.getDayTime());
    }

    public static String formatForecastMap(Map<ResourceLocation, float[][]> forecastMap) {
        StringBuilder sb = new StringBuilder("[Temperature Forecast per Biome]");
        for (Map.Entry<ResourceLocation, float[][]> entry : forecastMap.entrySet()) {
            ResourceLocation key = entry.getKey();
            float[][] week = entry.getValue();
            sb.append(weekForecastToString(key, week));
        }
        return sb.toString();
    }

    private static String weekForecastToString(ResourceLocation biome, float[][] week) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(biome).append(":");
        for (int day = 0; day < week.length; day++) {
            sb.append("\n  Day ").append(day + 1).append(": ");
            float[] profile = week[day];
            for (int i = 0; i < profile.length; i++) {
                sb.append(net.Gabou.projectatmosphere.util.UnitFormatter.formatTemperature(profile[i]));
                if (i < profile.length - 1) sb.append(", ");
            }
        }
        return sb.toString();
    }

    public static boolean isInOverworld(Level level) {
        return level.dimension().equals(Level.OVERWORLD);
    }
}


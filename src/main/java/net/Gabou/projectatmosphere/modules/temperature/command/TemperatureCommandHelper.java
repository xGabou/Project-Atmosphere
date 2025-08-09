package net.Gabou.projectatmosphere.modules.temperature.command;

import net.Gabou.projectatmosphere.ProjectAtmosphere;

import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.temperature.compat.SereneTempToCelcius;

import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonHandler;
import sereneseasons.season.SeasonHooks;
import sereneseasons.season.SeasonTime;

import java.util.Locale;
import java.util.Map;

public class TemperatureCommandHelper {

    /**
     * Get the resource location of the biome at the player’s current position.
     */
    public static BiomeInstanceKey getCurrentBiome(Player player) {
        return new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(player.blockPosition(),player.level()), player.blockPosition());
    }

    /**
     * Get the biome from a user-entered string or resolve "current" to the player's current biome.
     */
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

    /**
     * Get the current tick of the Minecraft day, capped to 24000.
     */
    public static long getCurrentTick(ServerLevel level) {
        return level.getDayTime() % 24000L;
    }

    /**
     * Get the current temperature of a biome at a given tick.
     */
    public static float getTemperatureAt(BiomeInstanceKey biome, long tick) {
        return ForecastOrchestrator.getCurrentTemperature(biome, tick);
    }

    /**
     * Return the weekly forecast matrix for a given biome.
     */
    public static String getWeeklyForecast(BiomeInstanceKey biome) {
        return weekForecastToString(biome,ForecastGenerator.getForecast(biome).getTemperature());
    }

    /**
     * Return the daily temperature profile (curve) for a given biome.
     */
    public static float[] getDayProfile(BiomeInstanceKey biome) {
        return ForecastGenerator.getForecastMap().get(biome).getTemperatureDay();
    }

    /**
     * Get the current sub-season name (e.g., EARLY_SPRING, MID_SUMMER, etc.).
     */
    public static String getCurrentSubSeason(ServerLevel level) {
        var data = SeasonHandler.getSeasonSavedData(level);
        var time = new SeasonTime(data.seasonCycleTicks);
        return time.getSubSeason().toString();
    }

    /**
     * Get Serene Seasons raw temperature at a given position.
     */
    public static float getFinalBiomeTemperature(Level level, Holder<Biome> biomeHolder, BlockPos pos) {
        return ModConfig.seasons.isDimensionWhitelisted(level.dimension())
                && !biomeHolder.is(sereneseasons.init.ModTags.Biomes.BLACKLISTED_BIOMES)
                ? SeasonHooks.getBiomeTemperature(level, biomeHolder, pos)  
                : biomeHolder.value().getBaseTemperature();
    }


    /**
     * Convert Serene raw temp (-0.5 to 2.0) to Celsius using custom logic.
     */
    public static double convertToCelsius(float sereneTemp) {
        return SereneTempToCelcius.SereneTempToCelcius(sereneTemp);
    }

    /**
     * Get the final computed Celsius temperature used by the mod (includes time of day, fluctuations, etc).
     */
    public static float getRealTemperature(ServerLevel level, BiomeInstanceKey biome, BlockPos pos) {
        return ForecastOrchestrator.getCurrentTemperature(biome, level.getDayTime());
    }


    public static String formatForecastMap(Map<BiomeInstanceKey, float[][]> forecastMap) {
        StringBuilder sb = new StringBuilder("§6[Temperature Forecast per Biome]");

        for (Map.Entry<BiomeInstanceKey, float[][]> entry : forecastMap.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            float[][] week = entry.getValue();

            sb.append(weekForecastToString(key, week));
        }

        return sb.toString();
    }



    private static String weekForecastToString(BiomeInstanceKey biome, float[][] week) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n§e").append(biome.biomeType()).append(":");

        for (int day = 0; day < week.length; day++) {
            sb.append("\n  §7Day ").append(day + 1).append(" → ");
            float[] profile = week[day];

            
            for (int i = 0; i < profile.length; i++) {
                sb.append(String.format(Locale.US, "%.1f°C", profile[i]));
                if (i < profile.length - 1) sb.append(", ");
            }
        }
        return sb.toString();
    }
}

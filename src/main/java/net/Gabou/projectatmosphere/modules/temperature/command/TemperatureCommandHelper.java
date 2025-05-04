package net.Gabou.projectatmosphere.modules.temperature.command;

import net.Gabou.projectatmosphere.modules.temperature.manager.TemperatureManager;
import net.Gabou.projectatmosphere.modules.temperature.compat.SereneTempToCelcius;
import net.Gabou.projectatmosphere.modules.temperature.forecast.TemperatureForecast;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
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
    public static ResourceLocation getCurrentBiome(Player player) {
        return player.level().getBiome(player.blockPosition()).unwrapKey().get().location();
    }

    /**
     * Get the biome from a user-entered string or resolve "current" to the player's current biome.
     */
    public static ResourceLocation resolveBiome(Player player, String biomeStr) {
        if (biomeStr.equalsIgnoreCase("current") || biomeStr.equalsIgnoreCase("currentbiome")) {
            return getCurrentBiome(player);
        }
        return new ResourceLocation(biomeStr);
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
    public static float getTemperatureAt(ResourceLocation biome, long tick) {
        return TemperatureManager.getCurrentTemperature(biome, tick);
    }

    /**
     * Return the weekly forecast matrix for a given biome.
     */
    public static String getWeeklyForecast(ResourceLocation biome) {
        return weekForecastToString(biome,TemperatureManager.getWeeklyForecast(biome));
    }

    /**
     * Return the daily temperature profile (curve) for a given biome.
     */
    public static float[] getDayProfile(ResourceLocation biome) {
        return TemperatureProfileManager.getDayProfile(biome);
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
                ? SeasonHooks.getBiomeTemperature(level, biomeHolder, pos)  // Tu peux wrapper ça aussi si tu veux.
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
    public static float getRealTemperature(ServerLevel level, ResourceLocation biome, BlockPos pos) {
        return TemperatureGenerator.getRealTemperature(level, biome, pos);
    }

    /**
     * Forecast temperature based on surroundings (e.g., ahead of time).
     */
    public static String getForecastedTemperature(ServerLevel level, BlockPos pos) {
        return formatForecastMap(TemperatureForecast.generateForecastAround(level, pos, 500));
    }

    public static String formatForecastMap(Map<ResourceLocation, float[][]> forecastMap) {
        StringBuilder sb = new StringBuilder("§6[Temperature Forecast per Biome]");

        for (Map.Entry<ResourceLocation, float[][]> entry : forecastMap.entrySet()) {
            ResourceLocation biome = entry.getKey();
            float[][] week = entry.getValue();

            sb.append(weekForecastToString(biome, week));
        }
        return sb.toString();
    }

    private static String weekForecastToString(ResourceLocation biome, float[][] week) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n§e").append(biome).append(":");

        for (int day = 0; day < week.length; day++) {
            sb.append("\n  §7Day ").append(day + 1).append(" → ");
            float[] profile = week[day];

            // Format each value with 1 decimal
            for (int i = 0; i < profile.length; i++) {
                sb.append(String.format(Locale.US, "%.1f°C", profile[i]));
                if (i < profile.length - 1) sb.append(", ");
            }
        }
        return sb.toString();
    }
}

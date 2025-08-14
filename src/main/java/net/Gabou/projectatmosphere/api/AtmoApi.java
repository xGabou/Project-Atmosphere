package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Public-facing API for accessing Project Atmosphere forecasts and current weather.
 */
public class AtmoApi {

    private static final AtmoApi INSTANCE = new AtmoApi();

    private AtmoApi() {
        
    }

    /**
     * Gets the global instance of the Atmosphere API.
     * @return the API instance
     */
    public static AtmoApi getInstance() {
        return INSTANCE;
    }

    /**
     * Gets the weather forecast for the given position and level.
     * @param level The server level to sample
     * @param pos The position in the world
     * @return The BiomeForecast for that position
     */
    public BiomeForecast getWeatherForecast(ServerLevel level, BlockPos pos) {
        BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(level, pos);
        return ForecastGenerator.getClosestValidForecast(key, ForecastType.TEMPERATURE);
    }

    /**
     * Gets the current weather conditions at the given position.
     * @param level The server level
     * @param pos The position
     * @return A WeatherSnapshot representing current conditions (to be defined)
     */
    public Object getCurrentWeather(ServerLevel level, BlockPos pos) {
        
        return null;
    }

    /**
     * Gets any active weather alerts for the given position.
     * @param level The server level
     * @param pos The position
     * @return An object representing alerts (to be defined)
     */
    public Object getWeatherAlerts(ServerLevel level, BlockPos pos) {
        
        return null;
    }

    /**
     * Gets historical weather data (could return past forecasts or measurements).
     * @param level The server level
     * @param pos The position
     * @return Historical data object (to be defined)
     */
    public Object getWeatherHistory(ServerLevel level, BlockPos pos) {
        
        return null;
    }
}

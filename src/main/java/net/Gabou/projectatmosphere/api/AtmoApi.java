package net.Gabou.projectatmosphere.api;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.ForecastRegionId;
import net.Gabou.projectatmosphere.util.WeatherType;
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
     * @return The ForecastRegion for that position
     */
    public ForecastRegion getWeatherForecast(ServerLevel level, BlockPos pos, ForecastType forecastType) {
        return ForecastOrchestrator.getRegionForecast(level, pos);
    }

    /**
     * Region-id based accessor for callers that already have a region key.
     */
    public ForecastRegion getWeatherForecast(ServerLevel level, ForecastRegionId regionId) {
        return ForecastOrchestrator.getRegionOrchestrator(level).ensureLoaded(regionId);
    }

    /**
     * Gets the current weather conditions at the given position.
     * @param level The server level
     * @param pos The position
     * @return A WeatherSnapshot representing current conditions (to be defined)
     */
    public Object getCurrentWeather(ServerLevel level, BlockPos pos) {

        return CloudManager.get(level).getCloudTypeAtPosition(pos.getX(), pos.getZ());
    }


    /**
     * Checks if it is currently raining or thundering at the specified position in the given level.
     * @param level The server level to check
     * @param pos The position in the world
     * @return true if it is raining or thundering at the position, false otherwise
     */
    public boolean isRainingOrThundering(ServerLevel level, BlockPos pos) {

        return WeatherType.isRainy(CloudManager.get(level).getCloudTypeAtPosition(pos.getX(), pos.getZ()).getKey().id());
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


    /**
     * Checks if it is currently raining at the specified position in the given level.
     * @param level The server level to check
     * @param pos The position in the world
     * @return true if it is raining at the position, false otherwise
     */
    @Deprecated(since ="0.5.4.0", forRemoval = true)
    public boolean isRainningAt(ServerLevel level, BlockPos pos) {
        return CloudManager.get(level).isRainingAt(pos);
    }



    public boolean isRainningLevel(ServerLevel level, BlockPos pos) {
        return  CloudManager.get(level).getClouds().stream().anyMatch(cloud -> WeatherType.isRainy(cloud.getCloudTypeId()));
    }
}

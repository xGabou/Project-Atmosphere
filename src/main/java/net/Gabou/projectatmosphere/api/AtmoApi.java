package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.manager.AtmosphereWorldEffectsManager;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.core.WeatherType;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;

/**
 * Public-facing read-only API for accessing Project Atmosphere forecasts and current weather.
 * Callers should treat this as a query surface only; it must not be used to mutate backend weather state.
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

    // ---------------------------------------------------------------------
    // Forecast access
    // ---------------------------------------------------------------------
    /**
     * Gets the weather forecast for the given position and level.
     * This is a read-only lookup into the forecast system.
     * @param level The server level to sample
     * @param pos The position in the world
     * @return The ForecastRegion for that position
     */
    public ForecastRegion getWeatherForecast(ServerLevel level, BlockPos pos, ForecastType forecastType) {
        return ForecastOrchestrator.getRegionForecast(level, pos);
    }

    /**
     * Region-id based accessor for callers that already have a region key.
     * This is still a read-only view and should not mutate the region state.
     */
    public ForecastRegion getWeatherForecast(ServerLevel level, RegionInstanceKey regionKey) {
        return ForecastOrchestrator.getRegionOrchestrator(level).ensureLoaded(regionKey);
    }

    // ---------------------------------------------------------------------
    // Snapshot access
    // ---------------------------------------------------------------------
    /**
     * Gets the current weather conditions at the given position.
     * This returns a snapshot view and must not mutate climate state.
     * @param level The server level
     * @param pos The position
     * @return A WeatherSnapshot representing current conditions
     */
    public WeatherSnapshot getCurrentWeather(ServerLevel level, BlockPos pos) {
        return getWeatherSnapshot(level, pos, level.getGameTime());
    }

    /**
     * Snapshot accessor for current weather at a position.
     */
    public WeatherSnapshot getWeatherSnapshot(ServerLevel level, BlockPos pos, long gameTime) {
        if (level == null || pos == null) {
            return new WeatherSnapshot(0f, 0f, 0f, 0f, 0f, false, false);
        }
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);

        float cloudCover = 0f;
        float rainIntensity = 0f;
        float temperature;
        if (state != null) {
            cloudCover = state.getCloudCover();
            rainIntensity = state.getRainIntensity();
            temperature = state.getTemperature();
        } else {
            temperature = ForecastOrchestrator.getCurrentTemperature(level, pos, gameTime);
        }

        WindVector wind = ForecastOrchestrator.getWind(regionKey, gameTime);
        float windSpeed = wind == null ? 0f : wind.baseSpeed();
        float windAngle = wind == null ? 0f : wind.angleRadians();

        float stormFactor;
        if (state != null) {
            float rain = Math.min(1f, state.getRainIntensity());
            float cloud = state.getCloudCover();
            float windStrength = Math.min(1f, state.getWindStrength() / 18f);
            float lowPressure = Math.min(1f, Math.max(0f, (1013.25f - state.getPressure()) / 55f));
            stormFactor = (rain * 0.45f)
                    + (cloud * 0.3f)
                    + (windStrength * 0.15f)
                    + (lowPressure * 0.1f);
            stormFactor = Math.max(0f, Math.min(1f, stormFactor));
        } else {
            stormFactor = 0f;
        }
        boolean storming = rainIntensity >= 0.6f || stormFactor >= 0.7f;
        boolean snowing = rainIntensity > 0f && temperature <= 0f;

        return new WeatherSnapshot(cloudCover, rainIntensity, temperature, windSpeed, windAngle, storming, snowing);
    }

    // ---------------------------------------------------------------------
    // World effect registration
    // ---------------------------------------------------------------------
    /**
     * Registers a world effect handler without exposing the internal manager to callers.
     */
    public void registerWorldEffect(AtmosphereWorldEffect effect) {
        AtmosphereWorldEffectsManager.registerWorldEffect(effect);
    }

    // ---------------------------------------------------------------------
    // Convenience weather checks
    // ---------------------------------------------------------------------
    /**
     * Checks if it is currently raining or thundering at the specified position in the given level.
     * @param level The server level to check
     * @param pos The position in the world
     * @return true if it is raining or thundering at the position, false otherwise
     */
    public boolean isRainingOrThundering(ServerLevel level, BlockPos pos) {
        WeatherSnapshot snapshot = getWeatherSnapshot(level, pos, level.getGameTime());
        return snapshot.rainIntensity() > 0f || snapshot.isStorming();
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
        return CloudManager.get(level).getClouds().stream().anyMatch(cloud -> WeatherType.isRainy(cloud.getCloudTypeId()));
    }

    // ---------------------------------------------------------------------
    // Legacy placeholders
    // ---------------------------------------------------------------------
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

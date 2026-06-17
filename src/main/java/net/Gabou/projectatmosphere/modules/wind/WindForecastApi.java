package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;

/**
 * Region-first API for querying forecasted wind values.
 * Implementations must be server-side and independent from rendering/client code.
 */
public interface WindForecastApi {

    /**
     * Returns wind direction in degrees for the given region.
     * 0° points to +X and values rotate clockwise in world space conventions used by the wind module.
     */
    float getWindDirection(RegionInstanceKey region);

    /**
     * Returns base wind speed in meters per second for the given region.
     */
    float getWindSpeed(RegionInstanceKey region);

    /**
     * Returns wind direction in degrees for the given region at a specific game time.
     */
    float getWindDirection(RegionInstanceKey region, long gameTime);

    /**
     * Returns base wind speed in meters per second for the given region at a specific game time.
     */
    float getWindSpeed(RegionInstanceKey region, long gameTime);
}

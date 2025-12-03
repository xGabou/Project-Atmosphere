package net.Gabou.projectatmosphere.modules.region;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.world.phys.Vec3;

/**
 * Aggregated region-level curves for all forecast variables.
 */
public interface RegionCurves {
    float[][] temperatureWeek();

    float[][] humidityWeek();

    float[][] pressureWeek();

    WindVector[] windWeek();

    float[] stormWeek();

    float sampleTemperature(Vec3 inRegionPos, long gameTime, ForecastRegion.Section[] sections);

    float sampleHumidity(Vec3 inRegionPos, long gameTime, ForecastRegion.Section[] sections);

    float samplePressure(long gameTime, ForecastRegion.Section[] sections);

    WindVector sampleWind(long gameTime, ForecastRegion.Section[] sections);

    float sampleStorm(long gameTime, ForecastRegion.Section[] sections);
}

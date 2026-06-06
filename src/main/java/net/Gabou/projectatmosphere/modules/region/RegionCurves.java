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

    float sampleTemperature(Vec3 inRegionPos, long gameTime);

    float sampleHumidity(Vec3 inRegionPos, long gameTime);

    float samplePressure(long gameTime);

    WindVector sampleWind(long gameTime);

    float sampleStorm(long gameTime);
}

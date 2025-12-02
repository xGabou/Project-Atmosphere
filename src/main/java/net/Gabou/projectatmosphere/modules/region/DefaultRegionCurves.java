package net.Gabou.projectatmosphere.modules.region;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Default implementation sampling weekly min/max (two-column) curves and weekly wind lists.
 */
public final class DefaultRegionCurves implements RegionCurves {
    private final float[][] temperatureWeek;
    private final float[][] humidityWeek;
    private final float[][] pressureWeek;
    private final WindVector[] windWeek;
    private final float[] stormWeek;

    public DefaultRegionCurves(float[][] temperatureWeek,
                               float[][] humidityWeek,
                               float[][] pressureWeek,
                               WindVector[] windWeek,
                               float[] stormWeek) {
        this.temperatureWeek = temperatureWeek;
        this.humidityWeek = humidityWeek;
        this.pressureWeek = pressureWeek;
        this.windWeek = windWeek;
        this.stormWeek = stormWeek;
    }

    @Override
    public float[][] temperatureWeek() {
        return temperatureWeek;
    }

    @Override
    public float[][] humidityWeek() {
        return humidityWeek;
    }

    @Override
    public float[][] pressureWeek() {
        return pressureWeek;
    }

    @Override
    public WindVector[] windWeek() {
        return windWeek;
    }

    @Override
    public float[] stormWeek() {
        return stormWeek;
    }

    @Override
    public float sampleTemperature(Vec3 inRegionPos, long gameTime, ForecastRegion.Section[] sections) {
        return sampleTwoColumn(temperatureWeek, gameTime);
    }

    @Override
    public float sampleHumidity(Vec3 inRegionPos, long gameTime, ForecastRegion.Section[] sections) {
        return sampleTwoColumn(humidityWeek, gameTime);
    }

    @Override
    public float samplePressure(long gameTime, ForecastRegion.Section[] sections) {
        return sampleTwoColumn(pressureWeek, gameTime);
    }

    @Override
    public WindVector sampleWind(long gameTime, ForecastRegion.Section[] sections) {
        if (windWeek == null || windWeek.length == 0) {
            return WindVector.fromBase(0f, 0f);
        }
        int day = currentDay(gameTime, windWeek.length);
        return windWeek[day] == null ? WindVector.fromBase(0f, 0f) : windWeek[day];
    }

    @Override
    public float sampleStorm(long gameTime, ForecastRegion.Section[] sections) {
        if (stormWeek == null || stormWeek.length == 0) {
            return 0f;
        }
        return stormWeek[currentDay(gameTime, stormWeek.length)];
    }

    private static float sampleTwoColumn(float[][] week, long gameTime) {
        if (week == null || week.length == 0) {
            return 0f;
        }
        int day = currentDay(gameTime, week.length);
        float[] dayProfile = week[day];
        if (dayProfile == null || dayProfile.length < 2) {
            return 0f;
        }
        float dayTimeFraction = fractionOfDay(gameTime);
        return Mth.lerp(dayTimeFraction, dayProfile[0], dayProfile[1]);
    }

    private static int currentDay(long gameTime, int length) {
        long day = (gameTime / 24000L) % length;
        return (int) day;
    }

    private static float fractionOfDay(long gameTime) {
        long ticksInDay = gameTime % 24000L;
        return ticksInDay / 24000f;
    }
}

package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.util.Mth;

/**
 * Default implementation of {@link WindForecastApi} backed by {@link ForecastOrchestrator}.
 */
public final class RegionWindForecastApi implements WindForecastApi {

    public static final RegionWindForecastApi INSTANCE = new RegionWindForecastApi();

    private static final long DEFAULT_TICK = 0L;

    private RegionWindForecastApi() {
    }

    @Override
    public float getWindDirection(RegionInstanceKey region) {
        return getWindDirection(region, DEFAULT_TICK);
    }

    @Override
    public float getWindSpeed(RegionInstanceKey region) {
        return getWindSpeed(region, DEFAULT_TICK);
    }

    @Override
    public float getWindDirection(RegionInstanceKey region, long gameTime) {
        WindVector wind = ForecastOrchestrator.getWind(region, gameTime);
        float degrees = (float) Math.toDegrees(wind.angleRadians());
        return Mth.wrapDegrees(degrees);
    }

    @Override
    public float getWindSpeed(RegionInstanceKey region, long gameTime) {
        WindVector wind = ForecastOrchestrator.getWind(region, gameTime);
        return wind.baseSpeed();
    }
}

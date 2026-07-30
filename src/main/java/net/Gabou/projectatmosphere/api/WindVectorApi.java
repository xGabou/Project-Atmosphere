package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.wind.WindEngine;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;

/**
 * Small API wrapper for reading wind values without exposing engine internals.
 */
public final class WindVectorApi {
    private WindVectorApi() {}

    // ---------------------------------------------------------------------
    // Simple wind read helpers
    // ---------------------------------------------------------------------
    public record WindSample(float speedMps, float directionDeg) {}

    public static WindSample getSurface(RegionInstanceKey key, long gameTime) {
        var vector = WindEngine.getCurrentLowWindVector(key, gameTime, ForecastOrchestrator.getCurrentStormChance(key, gameTime));
        return new WindSample(vector.baseSpeed(), (float) Math.toDegrees(vector.angleRadians()));
    }

    public static WindSample getOrFallback(RegionInstanceKey key, long gameTime) {
        return getSurface(key, gameTime);
    }

    public static WindSample getAloftProxy(RegionInstanceKey key, ServerLevel level) {
        var vector = WindEngine.getCurrentHighWindVector(key, level.getGameTime());
        float dir = (float) Math.toDegrees(vector.angleRadians());
        float speed = vector.baseSpeed();
        return new WindSample(speed, dir);
    }
}

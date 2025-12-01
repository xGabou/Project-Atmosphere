package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.modules.wind.WindEngine;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

public final class WindVectorApi {
    private WindVectorApi() {}

    public record WindSample(float speedMps, float directionDeg) {}

    public static WindSample getSurface(BiomeInstanceKey key) {
        var vector = WindEngine.getCurrentLowWindVector(key, 0L);
        return new WindSample(vector.baseSpeed(), (float) Math.toDegrees(vector.angleRadians()));
    }

    public static WindSample getOrFallback(BiomeInstanceKey key) {
        return getSurface(key);
    }

    public static WindSample getAloftProxy(BiomeInstanceKey key, ServerLevel level) {
        var vector = WindEngine.getCurrentHighWindVector(key, level.getGameTime());
        float dir = (float) Math.toDegrees(vector.angleRadians());
        float speed = vector.baseSpeed();
        return new WindSample(speed, dir);
    }
}


package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

public final class WindVector {
    private WindVector() {}

    public record WindSample(float speedMps, float directionDeg) {}

    public static WindSample getSurface(BiomeInstanceKey key, ServerLevel level) {
        net.Gabou.projectatmosphere.modules.core.WindVector w =
                ForecastOrchestrator.getCurrentWind(key, level.getGameTime());
        if (w == null) return new WindSample(0f, 0f);
        return new WindSample(w.baseSpeed(), (float) Math.toDegrees(w.angleRadians()));
    }

    public static WindSample getOrFallback(BiomeInstanceKey key, ServerLevel level) {
        return getSurface(key, level);
    }

    public static WindSample getAloftProxy(BiomeInstanceKey key, ServerLevel level) {
        WindSample surface = getSurface(key, level);
        float dir = surface.directionDeg() + (level.random.nextFloat() * 20f - 10f);
        float speed = surface.speedMps() + 5f;
        return new WindSample(speed, dir);
    }
}


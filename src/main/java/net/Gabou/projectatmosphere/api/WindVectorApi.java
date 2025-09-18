package net.Gabou.projectatmosphere.api;

import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

public final class WindVectorApi {
    private WindVectorApi() {}

    public record WindSample(float speedMps, float directionDeg) {}

    public static WindSample getSurface(BiomeInstanceKey key) {
        net.Gabou.projectatmosphere.modules.core.WindVector.WindSample w =
                net.Gabou.projectatmosphere.modules.core.WindVector.getOrFallback(key);
        return new WindSample(w.speedMps(), w.directionDeg());
    }

    public static WindSample getOrFallback(BiomeInstanceKey key) {
        return getSurface(key);
    }

    public static WindSample getAloftProxy(BiomeInstanceKey key, ServerLevel level) {
        WindSample surface = getSurface(key);
        float dir = surface.directionDeg() + (level.random.nextFloat() * 20f - 10f);
        float speed = surface.speedMps() + 5f;
        return new WindSample(speed, dir);
    }
}
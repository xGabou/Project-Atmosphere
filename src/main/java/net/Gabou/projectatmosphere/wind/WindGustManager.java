package net.Gabou.projectatmosphere.wind;

import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

public final class WindGustManager {
    private WindGustManager() { }

    public static float stormGustMultiplier(BiomeInstanceKey key, ServerLevel lvl) {
        return WindConfig.STORM_GUST_MULT;
    }

    public static float maybeStartGust(WindRuntimeState s, WindForecast f, WindForecastPart p, float stormMult, long nowTick) {
        Float prob = f.getGustProb().get(p);
        if (prob == null) return 0f;
        double chance = prob * stormMult;
        if (new Random().nextDouble() < chance) {
            FloatRange range = f.getGustRanges().get(p);
            if (range != null) {
                float speed = range.random(new Random());
                s.setCurrentGustSpeed(speed);
                s.setGustEndTick(nowTick + (long) (WindConfig.GUST_MEAN_SEC * 20));
                return speed;
            }
        }
        return 0f;
    }

    public static void updateGustDecay(WindRuntimeState s, long nowTick, float decayPerSec) {
        if (s.getCurrentGustSpeed() <= 0f) return;
        if (nowTick >= s.getGustEndTick()) {
            s.setCurrentGustSpeed(0f);
            return;
        }
        float dec = decayPerSec / 20f;
        s.setCurrentGustSpeed(Math.max(0f, s.getCurrentGustSpeed() - dec));
    }
}


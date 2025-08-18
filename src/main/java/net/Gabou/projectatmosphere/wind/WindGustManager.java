package net.Gabou.projectatmosphere.wind;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

public final class WindGustManager {
    private WindGustManager() { }

    public static float stormGustMultiplier(BiomeInstanceKey key, ServerLevel lvl) {
        float chance = ForecastOrchestrator.getCurrentStormChance(key, lvl.getGameTime());
        return chance > 0.5f ? WindConfig.stormGustMult() : 1f;
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
                s.setGustEndTick(nowTick + (long) (WindConfig.gustMeanSec() * 20));
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

    public static void tick(WindRuntimeState s, WindForecast f, WindForecastPart p, BiomeInstanceKey key, ServerLevel lvl, long nowTick) {
        float mult = stormGustMultiplier(key, lvl);
        if (s.getCurrentGustSpeed() <= 0f) {
            maybeStartGust(s, f, p, mult, nowTick);
        } else {
            updateGustDecay(s, nowTick, WindConfig.gustDecayMps());
        }
    }
}


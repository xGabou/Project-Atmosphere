package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

public final class WindEngine {
    private static final Map<BiomeInstanceKey, WindForecast> FORECASTS = new HashMap<>();
    private static final Map<BiomeInstanceKey, WindRuntimeState> STATES = new HashMap<>();

    private WindEngine() { }

    public static void init() {
        // load storage if needed
    }

    public static WindRuntimeState getOrCreateRuntime(BiomeInstanceKey key, ServerLevel level) {
        return STATES.computeIfAbsent(key, k -> new WindRuntimeState());
    }

    public static void tick(ServerLevel level) {
        WindForecastPart part = resolvePart(level);
        long now = level.getGameTime();
        for (Map.Entry<BiomeInstanceKey, WindRuntimeState> entry : STATES.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            WindRuntimeState state = entry.getValue();
            WindForecast forecast = FORECASTS.get(key);
            if (forecast == null) {
                WindVector.WindSample sample = WindVector.getOrFallback(key, level);
                WindVector.set(key, sample.speedMps(), sample.directionDeg());
                continue;
            }
            if (state.getNextRetargetTick() <= now) {
                FloatRange base = forecast.getBaseRanges().get(part);
                if (base != null) {
                    state.setTargetBaseSpeed(base.random(new java.util.Random()));
                }
                FloatRange dir = forecast.getDirRangesDeg().get(part);
                if (dir != null) {
                    state.setTargetDirectionDeg(dir.random(new java.util.Random()));
                }
                state.setNextRetargetTick(now + (long) (WindConfig.baseRetargetSec() * 20));
            }
            float speed = state.getCurrentBaseSpeed() + (state.getTargetBaseSpeed() - state.getCurrentBaseSpeed()) * 0.1f;
            state.setCurrentBaseSpeed(speed);
            float dirCur = state.getCurrentDirectionDeg() + (state.getTargetDirectionDeg() - state.getCurrentDirectionDeg()) * 0.1f;
            state.setCurrentDirectionDeg(dirCur);

            WindGustManager.tick(state, forecast, part, key, level, now);
            float effective = state.getCurrentBaseSpeed() + state.getCurrentGustSpeed();
            WindVector.set(key, effective, state.getCurrentDirectionDeg());
        }
    }

    public static WindForecast getForecast(BiomeInstanceKey key) {
        return FORECASTS.get(key);
    }

    public static void putForecast(BiomeInstanceKey key, WindForecast forecast) {
        FORECASTS.put(key, forecast);
    }

    public static WindForecastPart resolvePart(ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        if (time < 4000L) return WindForecastPart.MORNING;
        if (time < 8000L) return WindForecastPart.NOON;
        if (time < 12000L) return WindForecastPart.AFTERNOON;
        if (time < 16000L) return WindForecastPart.EVENING;
        if (time < 20000L) return WindForecastPart.MIDNIGHT;
        return WindForecastPart.NIGHT;
    }

    public static void syncToClients(BiomeInstanceKey key, ServerLevel level) {
        // networking stub
    }
}


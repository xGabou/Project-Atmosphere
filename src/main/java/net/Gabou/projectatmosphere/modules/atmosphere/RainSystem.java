package net.Gabou.projectatmosphere.modules.atmosphere;

import net.minecraft.server.level.ServerLevel;

public final class RainSystem {
    private static final float COOLING_SCALE = 3f;
    private static final float HUMIDITY_DRAIN = 0.2f;
    private static final float PRESSURE_RESTORE = 1.5f;
    private static final float RAIN_FADE = 0.01f;

    private RainSystem() {
    }

    public static void update(ServerLevel level) {
        if (AtmosphericStateRegistry.isEmpty()) {
            return;
        }

        for (RegionAtmosphereState state : AtmosphericStateRegistry.getStates()) {
            float intensity = state.getRainIntensity();
            if (intensity <= 0f) {
                state.dampenRain(RAIN_FADE);
                continue;
            }

            float clamped = Math.min(1f, intensity);
            state.adjustTemperature(-clamped * COOLING_SCALE);
            state.adjustHumidity(-clamped * HUMIDITY_DRAIN);
            state.adjustPressure(clamped * PRESSURE_RESTORE);
            state.dampenRain(RAIN_FADE);
        }
    }
}

package net.Gabou.projectatmosphere.modules.atmosphere;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

public final class SunlightController {
    private SunlightController() {
    }

    public static void update(ServerLevel level) {
        if (AtmosphericStateRegistry.isEmpty()) {
            return;
        }

        long dayTime = level.getDayTime();
        float daylight = baseDaylightCurve(dayTime);
        float seasonal = seasonalTilt(dayTime);

        for (RegionAtmosphereState state : AtmosphericStateRegistry.getStates()) {
            float cloudCover = state.getCloudCover();
            float rainIntensity = state.getRainIntensity();

            float biomeScale = state.getBiomeSunlightMultiplier();
            float sunlightFactor = daylight * biomeScale * seasonal;
            sunlightFactor *= Math.max(0f, 1f - cloudCover);
            sunlightFactor = Mth.clamp(sunlightFactor, 0f, 1f);
            state.setSunlight(sunlightFactor);

            float baseTarget = state.getSunlightDrivenTemperature(sunlightFactor);
            float rainPenalty = rainIntensity * state.getBaselineTemperatureSpan() * 0.15f;
            float adjustedTarget = baseTarget - rainPenalty;
            float blended = Mth.lerp(0.35f, state.getTemperature(), adjustedTarget);
            state.setTemperature(blended);

            float humidityDelta = (rainIntensity * 0.02f) - (sunlightFactor * 0.01f);
            state.adjustHumidity(humidityDelta);

            state.relaxTowardBase(0.0005f);
            state.recordDailySnapshot(dayTime);
        }
    }

    private static float baseDaylightCurve(long dayTime) {
        long time = dayTime % 24000L;
        float dayProgress = time / 12000f;
        float daylight = (float) Math.sin(Math.PI * dayProgress);
        daylight = Mth.clamp(daylight, 0f, 1f);
        return daylight * daylight;
    }

    private static float seasonalTilt(long dayTime) {
        long day = dayTime / 24000L;
        float seasonProgress = (day % 96L) / 96f;
        return 0.7f + 0.3f * Mth.cos(seasonProgress * (float) (Math.PI * 2));
    }
}

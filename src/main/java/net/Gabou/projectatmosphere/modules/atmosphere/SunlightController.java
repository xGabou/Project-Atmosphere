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
        float sunAngle = level.getSunAngle(1f);
        float daylight = baseDaylightCurve(sunAngle);
        float seasonal = seasonalTilt(level);

        for (RegionAtmosphereState state : AtmosphericStateRegistry.getStates()) {
            float cloudCover = state.getCloudCover();
            float rainIntensity = state.getRainIntensity();

            float biomeScale = state.getBiomeSunlightMultiplier();
            float sunlightFactor = daylight * biomeScale * seasonal;
            sunlightFactor *= Math.max(0f, 1f - cloudCover);
            sunlightFactor = Math.max(0f, sunlightFactor);
            state.setSunlight(Math.min(sunlightFactor, 1f));

            state.adjustTemperature(sunlightFactor * 6f);
            state.adjustHumidity(sunlightFactor * 0.02f);
            state.adjustTemperature(-cloudCover * 6f);
            state.adjustHumidity(-rainIntensity * 0.05f);

            state.relaxTowardBase(0.0005f);
            state.recordDailySnapshot(dayTime);
        }
    }

    private static float baseDaylightCurve(float sunAngle) {
        float cosine = (float) Math.cos(sunAngle);
        float daylight = Mth.clamp(cosine, 0f, 1f);
        return daylight * daylight;
    }

    private static float seasonalTilt(ServerLevel level) {
        long day = level.getDayTime() / 24000L;
        float seasonProgress = (day % 96L) / 96f;
        return 0.85f + 0.15f * Mth.cos(seasonProgress * (float) (Math.PI * 2));
    }
}

package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.minecraft.util.Mth;

/**
 * Unified wind forecast describing both the upper-level (high) and ground-level (low) wind curves.
 * The forecast is derived from the weekly wind curve stored on a ForecastRegion.
 */
public final class WindForecast {
    public static final class WindSlice {
        private final float highBase;
        private final float highGust;
        private final float lowBase;
        private final float lowGust;
        private final float directionDeg;

        public WindSlice(float highBase, float highGust, float lowBase, float lowGust, float directionDeg) {
            this.highBase = highBase;
            this.highGust = highGust;
            this.lowBase = lowBase;
            this.lowGust = lowGust;
            this.directionDeg = directionDeg;
        }

        public float highBase() {
            return highBase;
        }

        public float highGust() {
            return highGust;
        }

        public float lowBase() {
            return lowBase;
        }

        public float lowGust() {
            return lowGust;
        }

        public float directionDeg() {
            return directionDeg;
        }
    }

    private final WindSlice[] slices;
    private final float gustProbability;
    private final float stormGustProbability;
    private final float spikeProbability;

    public WindForecast(WindSlice[] slices, float gustProbability, float stormGustProbability, float spikeProbability) {
        this.slices = slices;
        this.gustProbability = gustProbability;
        this.stormGustProbability = stormGustProbability;
        this.spikeProbability = spikeProbability;
    }

    public WindSlice sliceForDay(long worldTime) {
        int day = (int) ((worldTime / 24000L) % slices.length);
        return slices[Math.max(0, Math.min(day, slices.length - 1))];
    }

    public float gustProbability() {
        return gustProbability;
    }

    public float stormGustProbability() {
        return stormGustProbability;
    }

    public float spikeProbability() {
        return spikeProbability;
    }

    public WindVector sampleHigh(long worldTime) {
        WindSlice slice = sliceForDay(worldTime);
        float base = dailyCurve(slice.highBase(), worldTime);
        float gust = dailyCurve(slice.highGust(), worldTime);
        float dirRad = (float) Math.toRadians(slice.directionDeg());
        return new WindVector(base, dirRad, gust);
    }

    public WindVector sampleLow(long worldTime) {
        WindSlice slice = sliceForDay(worldTime);
        float base = dailyCurve(slice.lowBase(), worldTime);
        float gust = dailyCurve(slice.lowGust(), worldTime);
        float dirRad = (float) Math.toRadians(slice.directionDeg());
        return new WindVector(base, dirRad, gust);
    }

    private static float dailyCurve(float base, long worldTime) {
        float timeOfDay = (worldTime % 24000L) / 24000f;
        float wave = (float) Math.sin(timeOfDay * (float) (Math.PI * 2));
        float dailyScale = 0.8f + 0.2f * wave;
        return Math.max(0f, base * dailyScale);
    }

    public static WindForecast fromRegionForecast(ForecastRegion regionForecast) {
        if (regionForecast == null) {
            return new WindForecast(new WindSlice[]{new WindSlice(0f, 0f, 0f, 0f, 0f)}, 0f, 0f, 0f);
        }

        WindVector[] week = regionForecast.getWind();
        if (week == null || week.length == 0) {
            return new WindForecast(new WindSlice[]{new WindSlice(0f, 0f, 0f, 0f, 0f)}, 0f, 0f, 0f);
        }

        WindSlice[] slices = new WindSlice[week.length];
        float baseProb = 0.15f;
        for (int i = 0; i < week.length; i++) {
            WindVector vector = week[i] == null ? WindVector.fromBase(0f, 0f) : week[i];
            float baseSpeed = vector.baseSpeed();
            float gustSpeed = Math.max(vector.gustSpeed(), baseSpeed);
            float dirDeg = (float) Math.toDegrees(vector.angleRadians());

            float highBase = baseSpeed;
            float highGust = gustSpeed;
            float lowBase = baseSpeed * 0.65f;
            float lowGust = lowBase + (gustSpeed - baseSpeed) * 0.55f;

            slices[i] = new WindSlice(highBase, highGust, lowBase, lowGust, dirDeg);
            baseProb = Math.max(baseProb, Math.min(0.6f, (gustSpeed - baseSpeed) * 0.02f));
        }

        float spikeChance = Mth.clamp(baseProb * 0.5f, 0.05f, 0.35f);
        float stormProb = Mth.clamp(baseProb * 1.5f, baseProb, 0.8f);
        return new WindForecast(slices, baseProb, stormProb, spikeChance);
    }
}

package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.modules.core.WindVector;

/**
 * Produces smooth upper-level wind vectors used for cloud motion and visuals.
 */
public final class HighWindModel {
    private HighWindModel() { }

    public static WindVector sample(WindForecast forecast, WindRuntimeState runtime, long worldTime) {
        WindVector target = forecast.sampleHigh(worldTime);
        float speed = blend(runtime.getCurrentHighSpeed(), target.baseSpeed());
        float dirDeg = blendAngle(runtime.getCurrentHighDirectionDeg(), (float) Math.toDegrees(target.angleRadians()));

        runtime.setCurrentHighSpeed(speed);
        runtime.setCurrentHighDirectionDeg(dirDeg);
        return new WindVector(speed, (float) Math.toRadians(dirDeg), target.gustSpeed());
    }

    private static float blend(float current, float target) {
        return current + (target - current) * 0.1f;
    }

    private static float blendAngle(float currentDeg, float targetDeg) {
        float delta = wrapDegrees(targetDeg - currentDeg);
        return currentDeg + delta * 0.1f;
    }

    private static float wrapDegrees(float deg) {
        float wrapped = deg % 360f;
        if (wrapped <= -180f) wrapped += 360f;
        if (wrapped > 180f) wrapped -= 360f;
        return wrapped;
    }
}

package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * Ground-level wind model that adds gusts and keeps values smooth for player interactions.
 */
public final class LowWindModel {
    private LowWindModel() { }

    public static WindVector sample(WindForecast forecast, WindRuntimeState runtime, long worldTime, float stormChance) {
        WindVector target = forecast.sampleLow(worldTime);
        Random rng = new Random(worldTime + Float.floatToIntBits(target.baseSpeed()));

        if (!runtime.isGustActive()) {
            float chance = forecast.gustProbability();
            chance += stormChance * forecast.stormGustProbability();
            if (rng.nextFloat() < chance) {
                startGust(runtime, target, worldTime, rng, false);
            } else if (rng.nextFloat() < forecast.spikeProbability()) {
                startGust(runtime, target, worldTime, rng, true);
            }
        } else if (worldTime >= runtime.getGustEndTick()) {
            runtime.setGustActive(false);
            runtime.setCurrentGustBonus(0f);
        }

        if (runtime.isGustActive()) {
            float decay = runtime.getCurrentGustBonus() * 0.05f;
            runtime.setCurrentGustBonus(Math.max(0f, runtime.getCurrentGustBonus() - decay));
            if (runtime.getCurrentGustBonus() <= 0.01f) {
                runtime.setGustActive(false);
            }
        }

        float gustedSpeed = target.baseSpeed() + runtime.getCurrentGustBonus();
        float smoothedSpeed = blend(runtime.getCurrentLowSpeed(), gustedSpeed);
        float dirDeg = blendAngle(runtime.getCurrentLowDirectionDeg(), (float) Math.toDegrees(target.angleRadians()));

        runtime.setCurrentLowSpeed(smoothedSpeed);
        runtime.setCurrentLowDirectionDeg(dirDeg);
        return new WindVector(smoothedSpeed, (float) Math.toRadians(dirDeg), smoothedSpeed + 0.5f * (target.gustSpeed() - target.baseSpeed()));
    }

    private static void startGust(WindRuntimeState runtime, WindVector target, long worldTime, Random rng, boolean spike) {
        float gustHeadroom = Math.max(0f, target.gustSpeed() - target.baseSpeed());
        float multiplier = spike ? 1.2f : 0.6f + rng.nextFloat() * 0.6f;
        float gustAmount = gustHeadroom * multiplier;
        runtime.setCurrentGustBonus(gustAmount);
        runtime.setGustActive(true);
        long duration = spike ? 40L + rng.nextInt(40) : 80L + rng.nextInt(120);
        runtime.setGustEndTick(worldTime + duration);
    }

    private static float blend(float current, float target) {
        return current + (target - current) * 0.2f;
    }

    private static float blendAngle(float currentDeg, float targetDeg) {
        float delta = wrapDegrees(targetDeg - currentDeg);
        return currentDeg + delta * 0.2f;
    }

    private static float wrapDegrees(float deg) {
        float wrapped = Mth.wrapDegrees(deg);
        if (wrapped < -180f) wrapped += 360f;
        if (wrapped > 180f) wrapped -= 360f;
        return wrapped;
    }
}

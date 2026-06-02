package net.Gabou.projectatmosphere.modules.weather;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class StormMotionModel {
    private static final float TORNADO_BASE_DRIFT = 0.055F;
    private static final float TORNADO_TURN_RATE = 0.020F;
    private static final float TORNADO_WANDER_SCALE = 0.28F;
    private static final float TORNADO_AMBIENT_INFLUENCE = 0.32F;
    private static final float TORNADO_LEASH_RADIUS = 140.0F;
    private static final float HURRICANE_BASE_DRIFT = 0.06F;
    private static final float HURRICANE_TURN_RATE = 0.012F;
    private static final float HURRICANE_WANDER_SCALE = 0.25F;

    private StormMotionModel() {
    }

    public static Vec3 advanceTornado(UUID id, Vec3 position, Vec3 currentVelocity, WindVector ambientWind,
                                      float normalizedIntensity, long ageTicks, float anchorX, float anchorZ) {
        float ambientSpeed = Math.max(0.6F, ambientWind.baseSpeed());
        float ambientHeading = ambientWind.angleRadians();
        float currentHeading = currentVelocity.lengthSqr() > 1.0E-4
                ? (float) Math.atan2(currentVelocity.z, currentVelocity.x)
                : ambientHeading + noiseAngle(id, ageTicks, 0.003F, TORNADO_WANDER_SCALE);

        float selfHeading = currentHeading
                + noiseAngle(id, ageTicks, 0.0008F, TORNADO_WANDER_SCALE)
                + noiseAngle(id, ageTicks, 0.0026F, 0.12F);
        float heading = rotateTowards(currentHeading, selfHeading, TORNADO_TURN_RATE + normalizedIntensity * 0.010F);

        Vec3 selfVector = new Vec3(Math.cos(heading), 0.0D, Math.sin(heading))
                .scale(TORNADO_BASE_DRIFT + normalizedIntensity * 0.07F);

        Vec3 ambientVector = new Vec3(Math.cos(ambientHeading), 0.0D, Math.sin(ambientHeading))
                .scale(ambientSpeed * (0.012F + normalizedIntensity * 0.010F) * TORNADO_AMBIENT_INFLUENCE);

        Vec3 lateral = new Vec3(-selfVector.z, 0.0D, selfVector.x)
                .scale(noiseSigned(id, ageTicks, 0.0016F) * (0.015F + normalizedIntensity * 0.020F));

        float dx = anchorX - (float) position.x;
        float dz = anchorZ - (float) position.z;
        float anchorDist = Mth.sqrt(dx * dx + dz * dz);
        Vec3 leash = Vec3.ZERO;
        if (anchorDist > TORNADO_LEASH_RADIUS) {
            double leashStrength = Math.min((anchorDist - TORNADO_LEASH_RADIUS) * 0.0025D, 0.12D);
            leash = new Vec3(dx / Math.max(anchorDist, 0.001F), 0.0D, dz / Math.max(anchorDist, 0.001F)).scale(leashStrength);
        }

        Vec3 targetVelocity = selfVector.add(ambientVector).add(lateral).add(leash);
        Vec3 velocity = currentVelocity.lerp(targetVelocity, 0.08D);
        return position.add(velocity);
    }

    public static Vec3 advanceHurricane(UUID id, Vec3 position, Vec3 currentVelocity, WindVector ambientWind,
                                        float normalizedIntensity, long ageTicks) {
        float ambientSpeed = Math.max(1.0F, ambientWind.baseSpeed());
        float targetHeading = ambientWind.angleRadians() + noiseAngle(id, ageTicks, 0.0017F, HURRICANE_WANDER_SCALE);
        float currentHeading = currentVelocity.lengthSqr() > 1.0E-4 ? (float) Math.atan2(currentVelocity.z, currentVelocity.x) : targetHeading;
        float heading = rotateTowards(currentHeading, targetHeading, HURRICANE_TURN_RATE + normalizedIntensity * 0.004F);
        float speed = ambientSpeed * (HURRICANE_BASE_DRIFT + normalizedIntensity * 0.08F);

        Vec3 blended = new Vec3(
                Math.cos(heading) * speed,
                0.0D,
                Math.sin(heading) * speed
        );
        Vec3 velocity = currentVelocity.lerp(blended, 0.08D);
        return position.add(velocity);
    }

    public static float noise01(UUID id, long tick, float rate) {
        long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        float a = (float) Math.sin(seed * 0.00000013D + tick * rate);
        float b = (float) Math.sin(seed * 0.00000029D + tick * rate * 0.63D + 1.7D);
        return Mth.clamp((a * 0.6F + b * 0.4F) * 0.5F + 0.5F, 0.0F, 1.0F);
    }

    public static float noiseSigned(UUID id, long tick, float rate) {
        return noise01(id, tick, rate) * 2.0F - 1.0F;
    }

    private static float noiseAngle(UUID id, long tick, float rate, float scale) {
        return noiseSigned(id, tick, rate) * scale;
    }

    private static float rotateTowards(float current, float target, float maxTurn) {
        float delta = Mth.wrapDegrees((float) Math.toDegrees(target - current));
        float deltaRad = (float) Math.toRadians(Mth.clamp(delta, (float) Math.toDegrees(-maxTurn), (float) Math.toDegrees(maxTurn)));
        return current + deltaRad;
    }
}

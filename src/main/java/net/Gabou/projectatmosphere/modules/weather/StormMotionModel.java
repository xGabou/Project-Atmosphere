package net.Gabou.projectatmosphere.modules.weather;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class StormMotionModel {
    private static final float TORNADO_BASE_DRIFT = 0.050F;
    private static final float TORNADO_TURN_RATE = 0.024F;
    private static final float TORNADO_AMBIENT_INFLUENCE = 0.10F;
    private static final float TORNADO_LEASH_RADIUS = 160.0F;
    private static final float HURRICANE_BASE_DRIFT = 0.06F;
    private static final float HURRICANE_TURN_RATE = 0.012F;
    private static final float HURRICANE_WANDER_SCALE = 0.25F;

    private StormMotionModel() {
    }

    public static Vec3 advanceTornado(UUID id, Vec3 position, Vec3 currentVelocity, WindVector ambientWind,
                                      float normalizedIntensity, long ageTicks, float anchorX, float anchorZ) {
        return advanceTornado(null, id, position, currentVelocity, ambientWind, normalizedIntensity, 4, ageTicks, anchorX, anchorZ);
    }

    public static Vec3 advanceTornado(@Nullable ServerLevel level, UUID id, Vec3 position, Vec3 currentVelocity, WindVector ambientWind,
                                      float normalizedIntensity, int stormLevel, long ageTicks, float anchorX, float anchorZ) {
        float ambientSpeed = Math.max(0.6F, ambientWind.baseSpeed());
        float ambientHeading = ambientWind.angleRadians();
        float currentHeading = currentVelocity.lengthSqr() > 1.0E-4
                ? (float) Math.atan2(currentVelocity.z, currentVelocity.x)
                : ambientHeading;
        float stormNormalized = StormSeverityScale.toNormalized(stormLevel);
        SurfaceScore localSurface = level == null ? new SurfaceScore(0.0F, 0.0F, 0.0F) : sampleSurfaceScore(level, position, 8.0D + stormLevel * 1.5D);
        float localWater = localSurface.waterPenalty();

        float desiredHeading = level == null
                ? ambientHeading + noiseAngle(id, ageTicks, 0.0016F, 0.20F)
                : chooseTornadoGoalHeading(level, id, position, currentHeading, ambientHeading, normalizedIntensity, stormLevel, ageTicks, anchorX, anchorZ);

        float heading = rotateTowards(currentHeading, desiredHeading, TORNADO_TURN_RATE + normalizedIntensity * 0.012F + stormNormalized * 0.008F);
        Vec3 selfVector = horizontalVector(heading).scale(TORNADO_BASE_DRIFT + normalizedIntensity * 0.05F + stormNormalized * 0.02F);
        float ambientScale = TORNADO_AMBIENT_INFLUENCE * (1.0F - localWater * 0.85F);
        Vec3 ambientVector = horizontalVector(ambientHeading).scale(ambientSpeed * (0.004F + normalizedIntensity * 0.005F) * ambientScale);
        Vec3 shieldVector = level == null
                ? Vec3.ZERO
                : StormShieldManager.sampleAvoidance(level, position, 34.0D + stormLevel * 10.0D).scale(0.18D + stormNormalized * 0.16D);
        Vec3 waterVector = level == null
                ? Vec3.ZERO
                : sampleWaterAvoidance(level, position, 18.0D + stormLevel * 3.0D).scale(0.20D + stormNormalized * 0.24D + localWater * 0.26D);

        float dx = anchorX - (float) position.x;
        float dz = anchorZ - (float) position.z;
        float anchorDist = Mth.sqrt(dx * dx + dz * dz);
        Vec3 leash = Vec3.ZERO;
        if (anchorDist > TORNADO_LEASH_RADIUS) {
            double leashStrength = Math.min((anchorDist - TORNADO_LEASH_RADIUS) * 0.0030D, 0.16D);
            leash = new Vec3(dx / Math.max(anchorDist, 0.001F), 0.0D, dz / Math.max(anchorDist, 0.001F)).scale(leashStrength);
        }

        Vec3 targetVelocity = selfVector.add(ambientVector).add(shieldVector).add(waterVector).add(leash);
        Vec3 velocity = currentVelocity.lerp(targetVelocity, 0.12D);
        return position.add(velocity);
    }

    public static Vec3 advanceHurricane(UUID id, Vec3 position, Vec3 currentVelocity, WindVector ambientWind,
                                        float normalizedIntensity, long ageTicks) {
        return advanceHurricane(null, id, position, currentVelocity, ambientWind, normalizedIntensity, ageTicks);
    }

    public static Vec3 advanceHurricane(@Nullable ServerLevel level, UUID id, Vec3 position, Vec3 currentVelocity, WindVector ambientWind,
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
        if (level != null) {
            blended = blended.add(StormShieldManager.sampleAvoidance(level, position, 96.0D).scale(0.25D));
        }
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

    private static float chooseTornadoGoalHeading(ServerLevel level, UUID id, Vec3 position, float currentHeading, float ambientHeading,
                                                  float normalizedIntensity, int stormLevel, long ageTicks, float anchorX, float anchorZ) {
        float stormNormalized = StormSeverityScale.toNormalized(stormLevel);
        float baseHeading = rotateTowards(currentHeading, ambientHeading, 0.18F + stormNormalized * 0.08F);
        float probeDistance = 16.0F + stormLevel * 3.5F + normalizedIntensity * 10.0F;
        float headingStep = 0.24F + (1.0F - normalizedIntensity) * 0.04F;
        float bestScore = Float.NEGATIVE_INFINITY;
        float bestHeading = baseHeading;

        for (int i = -4; i <= 4; i++) {
            float candidate = baseHeading + headingStep * i + noiseAngle(id, ageTicks + i * 17L, 0.0009F, 0.08F);
            Vec3 ahead = position.add(horizontalVector(candidate).scale(probeDistance));
            SurfaceScore surface = sampleSurfaceScore(level, ahead, probeDistance * 0.35D);
            float continuity = 1.0F - Math.abs(wrapAngle(candidate - currentHeading)) / (float) Math.PI;
            float ambientAlign = 1.0F - Math.abs(wrapAngle(candidate - ambientHeading)) / (float) Math.PI;
            float anchorPenalty = 0.0F;
            float dx = anchorX - (float) ahead.x;
            float dz = anchorZ - (float) ahead.z;
            float anchorDist = Mth.sqrt(dx * dx + dz * dz);
            if (anchorDist > TORNADO_LEASH_RADIUS) {
                anchorPenalty = Mth.clamp((anchorDist - TORNADO_LEASH_RADIUS) / (TORNADO_LEASH_RADIUS * 1.2F), 0.0F, 1.0F);
            }
            float shieldPenalty = (float) StormShieldManager.getMaxProtection(level, ahead, 22.0D + stormLevel * 8.0D);
            float score = continuity * 0.42F
                    + ambientAlign * 0.08F
                    + surface.landScore * (1.05F + stormNormalized * 0.38F)
                    - surface.waterPenalty * 4.60F
                    - surface.reliefPenalty * 0.45F
                    - shieldPenalty * 2.60F
                    - anchorPenalty * 0.40F
                    - (surface.waterPenalty > 0.40F ? 1.60F : 0.0F)
                    + noiseSigned(id, ageTicks + i * 11L, 0.0013F) * 0.08F;
            if (score > bestScore) {
                bestScore = score;
                bestHeading = candidate;
            }
        }
        return bestHeading;
    }

    private static SurfaceScore sampleSurfaceScore(ServerLevel level, Vec3 center, double spread) {
        double[][] offsets = {
                {0.0D, 0.0D},
                {spread, 0.0D},
                {-spread, 0.0D},
                {0.0D, spread},
                {0.0D, -spread}
        };

        int landSamples = 0;
        int waterSamples = 0;
        int vegetationSamples = 0;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;

        for (double[] offset : offsets) {
            int x = Mth.floor(center.x + offset[0]);
            int z = Mth.floor(center.z + offset[1]);
            int terrainY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            minHeight = Math.min(minHeight, terrainY);
            maxHeight = Math.max(maxHeight, terrainY);

            BlockPos groundPos = new BlockPos(x, terrainY - 1, z);
            BlockPos surfacePos = new BlockPos(x, surfaceY - 1, z);
            BlockState ground = level.getBlockState(groundPos);
            BlockState surface = level.getBlockState(surfacePos);
            boolean water = surface.is(Blocks.WATER)
                    || ground.is(Blocks.WATER)
                    || surface.getFluidState().is(FluidTags.WATER)
                    || ground.getFluidState().is(FluidTags.WATER);
            if (water) {
                waterSamples++;
            } else {
                landSamples++;
            }

            if (ground.is(Blocks.GRASS_BLOCK)
                    || ground.is(Blocks.DIRT)
                    || ground.is(Blocks.COARSE_DIRT)
                    || ground.is(Blocks.PODZOL)
                    || ground.is(BlockTags.LOGS)
                    || ground.is(BlockTags.LEAVES)
                    || surface.is(BlockTags.FLOWERS)
                    || surface.is(BlockTags.CROPS)) {
                vegetationSamples++;
            }
        }

        float sampleCount = offsets.length;
        float landScore = (landSamples / sampleCount) + (vegetationSamples / sampleCount) * 0.30F;
        float waterPenalty = waterSamples / sampleCount;
        float reliefPenalty = minHeight == Integer.MAX_VALUE
                ? 0.0F
                : Mth.clamp((maxHeight - minHeight) / 16.0F, 0.0F, 1.0F);
        return new SurfaceScore(landScore, waterPenalty, reliefPenalty);
    }

    private static Vec3 horizontalVector(float heading) {
        return new Vec3(Math.cos(heading), 0.0D, Math.sin(heading));
    }

    private static Vec3 sampleWaterAvoidance(ServerLevel level, Vec3 center, double probeDistance) {
        Vec3 avoid = Vec3.ZERO;
        for (int i = 0; i < 8; i++) {
            float heading = (float) (i * (Math.PI * 2.0D / 8.0D));
            Vec3 dir = horizontalVector(heading);
            Vec3 probe = center.add(dir.scale(probeDistance));
            SurfaceScore score = sampleSurfaceScore(level, probe, probeDistance * 0.28D);
            if (score.waterPenalty > 0.0F) {
                avoid = avoid.add(dir.scale(-score.waterPenalty));
            }
        }
        return avoid.lengthSqr() > 1.0E-6 ? avoid.normalize() : Vec3.ZERO;
    }

    private static float noiseAngle(UUID id, long tick, float rate, float scale) {
        return noiseSigned(id, tick, rate) * scale;
    }

    private static float rotateTowards(float current, float target, float maxTurn) {
        float delta = Mth.wrapDegrees((float) Math.toDegrees(target - current));
        float deltaRad = (float) Math.toRadians(Mth.clamp(delta, (float) Math.toDegrees(-maxTurn), (float) Math.toDegrees(maxTurn)));
        return current + deltaRad;
    }

    private static float wrapAngle(float angle) {
        while (angle > Math.PI) {
            angle -= (float) (Math.PI * 2.0);
        }
        while (angle < -Math.PI) {
            angle += (float) (Math.PI * 2.0);
        }
        return angle;
    }

    private record SurfaceScore(float landScore, float waterPenalty, float reliefPenalty) {
    }
}

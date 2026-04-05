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
    private static final float TORNADO_LEASH_RADIUS = 160.0F;
    private static final int TORNADO_MIN_PLAN_TICKS = 100;
    private static final int TORNADO_MAX_PLAN_TICKS = 200;
    private static final float HURRICANE_BASE_DRIFT = 0.06F;
    private static final float HURRICANE_TURN_RATE = 0.012F;
    private static final float HURRICANE_WANDER_SCALE = 0.25F;

    private StormMotionModel() {
    }

    public static TornadoRoutePlan planTornadoRoute(ServerLevel level, UUID id, Vec3 position, WindVector ambientWind,
                                                    float normalizedIntensity, int stormLevel, float currentHeading,
                                                    long ageTicks, float anchorX, float anchorZ) {
        float ambientSpeed = Math.max(0.6F, ambientWind.baseSpeed());
        float ambientHeading = ambientWind.angleRadians();
        float stormNormalized = StormSeverityScale.toNormalized(stormLevel);
        float baseHeading = rotateTowards(
                currentHeading,
                ambientHeading,
                0.26F + normalizedIntensity * 0.10F + stormNormalized * 0.08F
        );
        int durationTicks = Mth.floor(Mth.lerp(
                noise01(id, ageTicks + 73L, 0.0007F),
                TORNADO_MIN_PLAN_TICKS,
                TORNADO_MAX_PLAN_TICKS
        ));
        float baseSpeed = TORNADO_BASE_DRIFT
                + normalizedIntensity * 0.05F
                + stormNormalized * 0.02F
                + Math.min(ambientSpeed, 30.0F) * 0.0007F;
        float headingStep = 0.22F + (1.0F - normalizedIntensity) * 0.05F;
        float bestScore = Float.NEGATIVE_INFINITY;
        Vec3 bestWaypoint = position;
        float bestHeading = baseHeading;
        float bestSpeed = baseSpeed;

        for (int i = -4; i <= 4; i++) {
            float candidateHeading = baseHeading
                    + headingStep * i
                    + noiseAngle(id, ageTicks + i * 17L, 0.0009F, 0.08F);
            float candidateSpeed = baseSpeed * (0.92F + noise01(id, ageTicks + i * 29L + 11L, 0.0011F) * 0.22F);
            double travelDistance = Math.max(
                    10.0D,
                    candidateSpeed * durationTicks * (0.86D + normalizedIntensity * 0.22D)
            );
            Vec3 candidateWaypoint = position.add(horizontalVector(candidateHeading).scale(travelDistance));
            candidateWaypoint = clampToAnchorLeash(candidateWaypoint, anchorX, anchorZ);

            float continuity = 1.0F - Math.abs(wrapAngle(candidateHeading - currentHeading)) / (float) Math.PI;
            float ambientAlign = 1.0F - Math.abs(wrapAngle(candidateHeading - ambientHeading)) / (float) Math.PI;
            float shieldPenalty = (float) StormShieldManager.getMaxProtection(level, candidateWaypoint, 22.0D + stormLevel * 8.0D);
            SurfaceScore surface = sampleSurfaceScore(level, candidateWaypoint, 8.0D + stormLevel * 1.8D);
            float waypointDx = anchorX - (float) candidateWaypoint.x;
            float waypointDz = anchorZ - (float) candidateWaypoint.z;
            float anchorDist = Mth.sqrt(waypointDx * waypointDx + waypointDz * waypointDz);
            float anchorPenalty = Mth.clamp(anchorDist / (TORNADO_LEASH_RADIUS * 0.96F), 0.0F, 1.0F);
            float score = continuity * 0.58F
                    + ambientAlign * 0.16F
                    + surface.landScore * (0.92F + stormNormalized * 0.38F)
                    - surface.waterPenalty * 4.80F
                    - surface.reliefPenalty * 0.55F
                    - shieldPenalty * 2.80F
                    - anchorPenalty * 0.35F
                    + noiseSigned(id, ageTicks + i * 13L, 0.0013F) * 0.05F;
            if (score > bestScore) {
                bestScore = score;
                bestHeading = candidateHeading;
                bestWaypoint = candidateWaypoint;
                bestSpeed = candidateSpeed;
            }
        }
        return new TornadoRoutePlan(bestWaypoint, bestHeading, bestSpeed, durationTicks);
    }

    public static TornadoRoutePlan planFallbackTornadoRoute(UUID id, Vec3 position, WindVector ambientWind,
                                                            float normalizedIntensity, int stormLevel, float currentHeading,
                                                            long ageTicks, float anchorX, float anchorZ) {
        float ambientSpeed = Math.max(0.6F, ambientWind.baseSpeed());
        float ambientHeading = ambientWind.angleRadians();
        float stormNormalized = StormSeverityScale.toNormalized(stormLevel);
        float desiredHeading = ambientHeading + noiseAngle(id, ageTicks, 0.0013F, 0.16F);
        float blendedHeading = rotateTowards(currentHeading, desiredHeading, 0.30F + stormNormalized * 0.08F);
        int durationTicks = Mth.floor(Mth.lerp(
                noise01(id, ageTicks + 29L, 0.0009F),
                TORNADO_MIN_PLAN_TICKS,
                TORNADO_MAX_PLAN_TICKS
        ));
        float speed = TORNADO_BASE_DRIFT
                + normalizedIntensity * 0.05F
                + stormNormalized * 0.02F
                + Math.min(ambientSpeed, 30.0F) * 0.0007F;
        double travelDistance = Math.max(
                10.0D,
                speed * durationTicks * (0.82D + normalizedIntensity * 0.18D)
        );
        Vec3 waypoint = clampToAnchorLeash(
                position.add(horizontalVector(blendedHeading).scale(travelDistance)),
                anchorX,
                anchorZ
        );
        return new TornadoRoutePlan(waypoint, blendedHeading, speed, durationTicks);
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

    private static Vec3 clampToAnchorLeash(Vec3 candidateWaypoint, float anchorX, float anchorZ) {
        double dx = candidateWaypoint.x - anchorX;
        double dz = candidateWaypoint.z - anchorZ;
        double distSqr = dx * dx + dz * dz;
        double leashRadius = TORNADO_LEASH_RADIUS * 0.94D;
        if (distSqr <= leashRadius * leashRadius) {
            return candidateWaypoint;
        }
        double dist = Math.sqrt(distSqr);
        double scale = leashRadius / Math.max(dist, 0.001D);
        return new Vec3(anchorX + dx * scale, candidateWaypoint.y, anchorZ + dz * scale);
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

    public record TornadoRoutePlan(Vec3 waypoint, float headingRadians, float speed, int durationTicks) {
    }

    private record SurfaceScore(float landScore, float waterPenalty, float reliefPenalty) {
    }
}

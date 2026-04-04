package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Applies combined low-level wind, gusts, and tornado forces to entities.
 */
public final class WindForces {
    private static final double MPS_TO_BLOCKS_PER_TICK = 1.0 / 20.0;
    private static final float PLAYER_MAX_DRIFT_BPT = 0.02f;
    private static final float ENTITY_MAX_DRIFT_BPT = 0.04f;
    private static final float PLAYER_WEIGHT_DIFF = 0.6f;
    private static final float ENTITY_WEIGHT_DIFF = 0.4f;
    private static final float ENTITY_GUST_BLEND = 0.3f;
    private static final java.util.Map<java.util.UUID, PlayerGustState> PLAYER_GUSTS = new java.util.concurrent.ConcurrentHashMap<>();

    private WindForces() { }

    public static void applyToPlayer(ServerLevel level, ServerPlayer player, float deltaTime) {
        if (player.isCreative() || player.isSpectator()) {
            PLAYER_GUSTS.remove(player.getUUID());
            return;
        }
        applyPlayerGusts(level, player);
    }

    public static void applyToEntity(ServerLevel level, LivingEntity entity, float deltaTime) {
        if (entity instanceof ServerPlayer) {
            return;
        }
        applyWindSteering(level, entity, WindConfig.pushThresholdMps(), ENTITY_WEIGHT_DIFF, WindConfig.entityPushScale(),
                ENTITY_MAX_DRIFT_BPT, deltaTime, true);
    }

    private static void applyWindSteering(ServerLevel level, LivingEntity entity, float thresholdMps, float weightDiff,
                                          float multiplier, float maxDriftBpt, float deltaTime, boolean allowGusts) {
        if (weightDiff <= 0f || multiplier <= 0f || maxDriftBpt <= 0f) {
            return;
        }
        float exposure = computeExposureFactor(level, entity);
        if (exposure <= 0f) {
            return;
        }
        WindVector wind = ForecastOrchestrator.getWind(level, entity.blockPosition(), level.getGameTime());
        float windSpeed = wind.baseSpeed();
        if (allowGusts) {
            float gustDelta = Math.max(0f, wind.gustSpeed() - windSpeed);
            windSpeed += gustDelta * ENTITY_GUST_BLEND;
        }
        float effectiveSpeed = windSpeed - thresholdMps;
        if (effectiveSpeed <= 0f) {
            return;
        }
        float ramp = WindConfig.pushRampMps();
        float rampFactor = ramp > 0f ? Mth.clamp(effectiveSpeed / ramp, 0f, 1f) : 1f;
        Vec3 direction = dirToVec(wind.angleRadians());
        if (direction.lengthSqr() < 1.0E-6) {
            return;
        }
        double targetSpeedBpt = effectiveSpeed * MPS_TO_BLOCKS_PER_TICK * rampFactor;
        Vec3 target = direction.scale(targetSpeedBpt);
        double targetLen = target.length();
        if (targetLen > maxDriftBpt) {
            target = target.scale(maxDriftBpt / targetLen);
        }

        Vec3 motion = entity.getDeltaMovement();
        Vec3 current = new Vec3(motion.x, 0.0, motion.z);
        double weight = Mth.clamp(weightDiff * multiplier * exposure * deltaTime * rampFactor, 0.0, 1.0);
        Vec3 dir = direction.normalize();
        double along = current.dot(dir);
        double deltaAlong = targetSpeedBpt - along;
        if (deltaAlong <= 0.0) {
            return;
        }
        Vec3 adjustment = dir.scale(deltaAlong * weight);
        Vec3 steered = current.add(adjustment);
        entity.setDeltaMovement(steered.x, motion.y, steered.z);
        entity.hurtMarked = true;
    }

    private static void applyPlayerGusts(ServerLevel level, ServerPlayer player) {
        float exposure = computeExposureFactor(level, player);
        java.util.UUID id = player.getUUID();
        if (exposure <= 0f) {
            PLAYER_GUSTS.remove(id);
            return;
        }
        WindVector wind = ForecastOrchestrator.getWind(level, player.blockPosition(), level.getGameTime());
        float baseSpeed = wind.baseSpeed();
        float threshold = WindConfig.playerWindThresholdMps();
        if (baseSpeed <= threshold) {
            PLAYER_GUSTS.remove(id);
            return;
        }
        float extremeThreshold = WindConfig.playerGustExtremeThresholdMps();
        if (player.isSprinting() && (extremeThreshold <= 0f || baseSpeed < extremeThreshold)) {
            PLAYER_GUSTS.remove(id);
            return;
        }

        PlayerGustState state = PLAYER_GUSTS.get(id);
        if (state == null || state.ticksRemaining <= 0) {
            float excess = baseSpeed - threshold;
            float chance = Mth.clamp(excess / WindConfig.playerGustChanceDivider(), 0f, 1f)
                    * WindConfig.playerGustChanceScale();
            if (extremeThreshold > 0f && baseSpeed >= extremeThreshold) {
                chance *= WindConfig.playerGustExtremeChanceMult();
            }
            if (level.getRandom().nextFloat() < chance) {
                int minTicks = WindConfig.playerGustDurationMin();
                int maxTicks = WindConfig.playerGustDurationMax();
                int duration = minTicks >= maxTicks ? minTicks
                        : minTicks + level.getRandom().nextInt(maxTicks - minTicks + 1);
                float varianceDeg = WindConfig.playerGustAngleVarianceDeg();
                float angle = wind.angleRadians();
                if (varianceDeg > 0f) {
                    float varianceRad = (float) Math.toRadians(varianceDeg);
                    float offset = (level.getRandom().nextFloat() * 2f - 1f) * varianceRad;
                    angle += offset;
                }
                Vec3 dir = dirToVec(angle);
                if (dir.lengthSqr() < 1.0E-6) {
                    return;
                }
                Vec3 normalized = dir.normalize();
                double strength = excess * WindConfig.playerGustStrengthScale() * MPS_TO_BLOCKS_PER_TICK;
                double maxBpt = WindConfig.playerMaxGustBpt();
                if (extremeThreshold > 0f && baseSpeed >= extremeThreshold) {
                    maxBpt *= WindConfig.playerGustExtremeStrengthMult();
                }
                strength = Mth.clamp(strength, 0.0, maxBpt);
                if (strength <= 0.0) {
                    return;
                }
                state = new PlayerGustState(duration, duration, normalized.x, normalized.z, strength);
                PLAYER_GUSTS.put(id, state);
            } else {
                return;
            }
        }

        double decay = state.totalTicks > 0 ? (double) state.ticksRemaining / state.totalTicks : 0.0;
        double impulse = state.strengthBpt * decay * exposure;
        double maxPerTick = WindConfig.playerMaxGustBpt();
        if (impulse > maxPerTick) {
            impulse = maxPerTick;
        }
        if (impulse > 0.0) {
            Vec3 motion = player.getDeltaMovement();
            Vec3 current = new Vec3(motion.x, 0.0, motion.z);
            Vec3 added = new Vec3(state.dirX * impulse, 0.0, state.dirZ * impulse);
            double currentLen = current.length();
            if (currentLen > 1.0E-6) {
                Vec3 currentDir = current.scale(1.0 / currentLen);
                double dot = currentDir.dot(added);
                if (dot < 0.0) {
                    added = added.subtract(currentDir.scale(dot));
                }
            }
            player.setDeltaMovement(motion.x + added.x, motion.y, motion.z + added.z);
            player.hurtMarked = true;
        }
        state.ticksRemaining--;
        if (state.ticksRemaining <= 0) {
            PLAYER_GUSTS.remove(id);
        }
    }

    private static float computeExposureFactor(ServerLevel level, LivingEntity entity) {
        if (entity.isInWaterOrBubble() || entity.isInLava()) {
            return 0f;
        }
        if (entity.horizontalCollision) {
            return 0f;
        }
        BlockPos headPos = BlockPos.containing(entity.getEyePosition());
        if (!level.canSeeSkyFromBelowWater(headPos)) {
            return 0f;
        }
        return 1f;
    }

    private static Vec3 dirToVec(float rad) {
        return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
    }

    private static final class PlayerGustState {
        private int ticksRemaining;
        private final int totalTicks;
        private final double dirX;
        private final double dirZ;
        private final double strengthBpt;

        private PlayerGustState(int ticksRemaining, int totalTicks, double dirX, double dirZ, double strengthBpt) {
            this.ticksRemaining = ticksRemaining;
            this.totalTicks = totalTicks;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.strengthBpt = strengthBpt;
        }
    }
}

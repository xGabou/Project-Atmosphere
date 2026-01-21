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
    private static final float PLAYER_THRESHOLD_MPS = 11.1f;
    private static final float PLAYER_MAX_DRIFT_BPT = 0.02f;
    private static final float ENTITY_MAX_DRIFT_BPT = 0.04f;
    private static final float PLAYER_WEIGHT_DIFF = 0.6f;
    private static final float ENTITY_WEIGHT_DIFF = 0.4f;
    private static final float ENTITY_GUST_BLEND = 0.3f;

    private WindForces() { }

    public static void applyToPlayer(ServerLevel level, ServerPlayer player, float deltaTime) {
        applyWindSteering(level, player, PLAYER_THRESHOLD_MPS, PLAYER_WEIGHT_DIFF, WindConfig.playerPushScale(),
                PLAYER_MAX_DRIFT_BPT, deltaTime, false);

        TornadoWindModel.TornadoForces tornado = WindEngine.getCurrentTornadoForce(player.position());
        if (tornado != null) {
            applyTornadoForce(player, tornado, deltaTime);
        }
    }

    public static void applyToEntity(ServerLevel level, LivingEntity entity, float deltaTime) {
        if (entity instanceof ServerPlayer) {
            return;
        }
        applyWindSteering(level, entity, WindConfig.pushThresholdMps(), ENTITY_WEIGHT_DIFF, WindConfig.entityPushScale(),
                ENTITY_MAX_DRIFT_BPT, deltaTime, true);
    }

    private static void applyTornadoForce(LivingEntity entity, TornadoWindModel.TornadoForces forces, float deltaTime) {
        double scale = deltaTime / 20f;
        Vec3 combined = forces.pullVector().add(forces.rotationVector()).scale(scale);
        Vec3 lift = forces.liftVector().scale(scale);
        entity.push(combined.x, lift.y, combined.z);
        entity.hurtMarked = true;
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
        Vec3 direction = dirToVec(wind.angleRadians());
        if (direction.lengthSqr() < 1.0E-6) {
            return;
        }
        double targetSpeedBpt = effectiveSpeed * MPS_TO_BLOCKS_PER_TICK;
        Vec3 target = direction.scale(targetSpeedBpt);
        double targetLen = target.length();
        if (targetLen > maxDriftBpt) {
            target = target.scale(maxDriftBpt / targetLen);
        }

        Vec3 motion = entity.getDeltaMovement();
        Vec3 current = new Vec3(motion.x, 0.0, motion.z);
        double weight = Mth.clamp(weightDiff * multiplier * exposure * deltaTime, 0.0, 1.0);
        Vec3 delta = current.subtract(target);
        Vec3 correction = delta.scale(weight);
        Vec3 steered = current.subtract(correction);
        entity.setDeltaMovement(steered.x, motion.y, steered.z);
        entity.hurtMarked = true;
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
}

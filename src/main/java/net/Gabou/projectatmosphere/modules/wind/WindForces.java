package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Applies combined low-level wind, gusts, and tornado forces to entities.
 */
public final class WindForces {
    private WindForces() { }

    public static void applyToPlayer(ServerLevel level, ServerPlayer player, float deltaTime) {
        BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(level, player.blockPosition());
        WindVector low = WindEngine.getCurrentLowWindVector(key, level.getGameTime());
        double windMagnitude = Math.max(low.baseSpeed(), low.gustSpeed());
        if (windMagnitude > WindConfig.pushThresholdMps()) {
            Vec3 push = dirToVec(low.angleRadians()).scale(windMagnitude * WindConfig.playerPushScale() * deltaTime / 20f);
            player.push(push.x, 0.0, push.z);
            player.hurtMarked = true;
        }

        TornadoWindModel.TornadoForces tornado = WindEngine.getCurrentTornadoForce(player.position());
        if (tornado != null) {
            applyTornadoForce(player, tornado, deltaTime);
        }
    }

    private static void applyTornadoForce(LivingEntity entity, TornadoWindModel.TornadoForces forces, float deltaTime) {
        double scale = deltaTime / 20f;
        Vec3 combined = forces.pullVector().add(forces.rotationVector()).scale(scale);
        Vec3 lift = forces.liftVector().scale(scale);
        entity.push(combined.x, lift.y, combined.z);
        entity.hurtMarked = true;
    }

    private static Vec3 dirToVec(float rad) {
        return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
    }
}

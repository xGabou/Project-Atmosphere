package net.Gabou.projectatmosphere.gameplay;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.wind.WindConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class WindPhysics {
    private WindPhysics() { }

    public static void onServerTick(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            applyIfStrong(level, p);
        }
    }

    private static void applyIfStrong(ServerLevel lvl, LivingEntity e) {
        BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(lvl, e.blockPosition());
        WindVector.WindSample w = WindVector.getOrFallback(key, lvl);
        if (w.speedMps() < WindConfig.pushThresholdMps()) return;
        Vec3 push = dirToVec(w.directionDeg()).scale(w.speedMps() * pushScale(e));
        e.push(push.x, 0.0, push.z);
        e.hurtMarked = true;
    }

    private static Vec3 dirToVec(float deg) {
        double rad = Math.toRadians(deg);
        return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
    }

    private static double pushScale(LivingEntity e) {
        return e instanceof ServerPlayer ? WindConfig.playerPushScale() : WindConfig.entityPushScale();
    }
}


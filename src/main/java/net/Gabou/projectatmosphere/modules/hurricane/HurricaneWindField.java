package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.modules.weather.StormShieldManager;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class HurricaneWindField {
    private static final double MIN_ENTITY_HEIGHT_OFFSET = -8.0D;
    private static final double MAX_ENTITY_HEIGHT = 128.0D;

    private HurricaneWindField() {
    }

    static void apply(HurricaneInstance hurricane, ServerLevel level, long gameTime) {
        if (!hurricane.markWindFieldTick(gameTime)) {
            return;
        }

        float stormIntensity = hurricane.getWindIntensity();
        if (stormIntensity <= 0.05F) {
            return;
        }

        double effectRadius = Mth.clamp(
                hurricane.getCoreRadius() * 0.82D,
                96.0D,
                248.0D + hurricane.category.ordinal() * 20.0D
        );
        double eyeRadius = hurricane.getVisualEyeRadius();
        AABB box = new AABB(
                hurricane.position.x - effectRadius, hurricane.position.y + MIN_ENTITY_HEIGHT_OFFSET, hurricane.position.z - effectRadius,
                hurricane.position.x + effectRadius, hurricane.position.y + MAX_ENTITY_HEIGHT, hurricane.position.z + effectRadius
        );
        Vec3 stormDrift = new Vec3(
                Math.cos(hurricane.wind.angleRadians()),
                0.0D,
                Math.sin(hurricane.wind.angleRadians())
        ).scale(Math.min(hurricane.wind.baseSpeed(), 40.0F) * (0.00045D + stormIntensity * 0.00025D));

        for (Entity entity : level.getEntities(null, box)) {
            if (!projectatmosphere$isAffectedEntity(level, entity)) {
                continue;
            }

            double dx = entity.getX() - hurricane.position.x;
            double dz = entity.getZ() - hurricane.position.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < 0.25D || distSq > effectRadius * effectRadius) {
                continue;
            }

            double dist = Math.sqrt(distSq);
            double inverseDist = 1.0D / dist;
            float centerFalloff = Mth.clamp((float) (1.0D - dist / effectRadius), 0.0F, 1.0F);
            float ringFactor = projectatmosphere$ringFactor((float) dist, (float) (eyeRadius * 1.10D), (float) (effectRadius * 0.74D));

            Vec3 inward = new Vec3(-dx * inverseDist, 0.0D, -dz * inverseDist);
            Vec3 tangential = new Vec3(-inward.z * hurricane.getRotationDirection(), 0.0D, inward.x * hurricane.getRotationDirection());

            double tangentialStrength = (0.028D + stormIntensity * 0.052D)
                    * (0.45D + centerFalloff * 0.25D + ringFactor * 0.30D);
            double inwardStrength = (0.007D + stormIntensity * 0.020D)
                    * (0.25D + centerFalloff * 0.75D);
            double liftStrength = (0.003D + stormIntensity * 0.010D)
                    * (0.10D + ringFactor * 0.90D);

            if (dist < eyeRadius * 0.90D) {
                tangentialStrength *= 0.35D;
                inwardStrength *= 0.45D;
                liftStrength *= 0.25D;
            }

            double entityScale = entity instanceof Player ? 0.82D : 1.0D;
            if (entity.onGround()) {
                entityScale *= 0.94D;
                liftStrength += 0.008D + stormIntensity * 0.006D;
            }

            Vec3 push = tangential.scale(tangentialStrength * entityScale)
                    .add(inward.scale(inwardStrength * entityScale))
                    .add(stormDrift)
                    .add(0.0D, liftStrength * entityScale, 0.0D);

            entity.push(push.x, push.y, push.z);
            entity.hasImpulse = true;
            entity.hurtMarked = true;
            if (push.y > 0.0D) {
                entity.fallDistance = 0.0F;
            }

            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }
        }
    }

    private static boolean projectatmosphere$isAffectedEntity(ServerLevel level, Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.noPhysics || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return !StormShieldManager.isProtected(level, entity.position());
    }

    private static float projectatmosphere$ringFactor(float radius, float innerRadius, float outerRadius) {
        if (outerRadius <= innerRadius) {
            return 0.0F;
        }
        float mid = (innerRadius + outerRadius) * 0.5F;
        float span = Math.max(1.0F, (outerRadius - innerRadius) * 0.5F);
        float normalized = 1.0F - Math.abs(radius - mid) / span;
        return Mth.clamp(normalized, 0.0F, 1.0F);
    }
}

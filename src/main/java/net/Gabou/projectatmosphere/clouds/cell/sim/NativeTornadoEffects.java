package net.Gabou.projectatmosphere.clouds.cell.sim;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Server-authoritative, non-destructive physics for native cell funnels.
 * The same funnelStrength synchronized to the renderer controls every force.
 */
public final class NativeTornadoEffects {
    private NativeTornadoEffects() {
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        for (CloudCell cell : CloudCellSimulationManager.getInstance().nativeTornadoCells(level)) {
            applyFunnel(level, cell);
        }
    }

    private static void applyFunnel(ServerLevel level, CloudCell cell) {
        float strength = cell.funnelStrength();
        if (strength <= 0.02F) {
            return;
        }
        double radius = Mth.clamp(10.0D + cell.radiusMinor() * 0.22D * strength, 10.0D, 72.0D);
        double bottom = cell.funnelGroundY();
        double top = Math.max(bottom + 24.0D, cell.baseY() + 12.0D);
        AABB area = new AABB(cell.x() - radius, bottom, cell.z() - radius,
                cell.x() + radius, top, cell.z() + radius);
        for (Entity entity : level.getEntities((Entity) null, area, NativeTornadoEffects::isAffected)) {
            double dx = cell.x() - entity.getX();
            double dz = cell.z() - entity.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal >= radius || horizontal < 0.001D) {
                continue;
            }
            double falloff = 1.0D - horizontal / radius;
            double height01 = Mth.clamp((entity.getY() - bottom) / Math.max(1.0D, top - bottom), 0.0D, 1.0D);
            double inward = (0.012D + 0.055D * strength) * falloff;
            double swirl = (0.018D + 0.075D * strength) * falloff;
            double inverseDistance = 1.0D / horizontal;
            double pullX = dx * inverseDistance * inward;
            double pullZ = dz * inverseDistance * inward;
            double tangentX = -dz * inverseDistance * swirl;
            double tangentZ = dx * inverseDistance * swirl;
            double lift = (0.010D + 0.075D * strength) * falloff * (1.0D - height01 * 0.55D);
            entity.push(pullX + tangentX, lift, pullZ + tangentZ);
            entity.hurtMarked = true;
        }
    }

    private static boolean isAffected(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        return !(entity instanceof ServerPlayer player && player.getAbilities().instabuild);
    }
}

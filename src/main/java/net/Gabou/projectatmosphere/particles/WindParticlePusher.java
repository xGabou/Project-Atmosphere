package net.Gabou.projectatmosphere.particles;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Shared helper to sample wind and return a target velocity in blocks per tick.
 * The push only occurs near players and when there is clear line-of-sight from an upwind
 * sample position, to avoid wind passing through walls.
 */
public final class WindParticlePusher {

    private static final double NEAR_PLAYER_RADIUS = 24.0;
    private static final double LINE_OF_SIGHT_CHECK = 4.0;
    private static final double MPS_TO_BLOCKS_PER_TICK = 1.0 / 20.0;
    private static final Map<RegionInstanceKey, WindVector> WIND_CACHE = new ConcurrentHashMap<>();
    private static volatile long WIND_CACHE_TICK = Long.MIN_VALUE;

    private WindParticlePusher() { }

    public static Vec3 computeWindPush(Level level, Vec3 position) {
        Player player = level.getNearestPlayer(position.x, position.y, position.z, NEAR_PLAYER_RADIUS, false);
        if (player == null) {
            return Vec3.ZERO;
        }

        BlockPos samplePos = BlockPos.containing(position);
        long gameTime = level.getGameTime();
        if (gameTime != WIND_CACHE_TICK) {
            WIND_CACHE.clear();
            WIND_CACHE_TICK = gameTime;
        }
        RegionInstanceKey regionKey = RegionInstanceKey.from(samplePos);
        WindVector wind = WIND_CACHE.computeIfAbsent(regionKey, key -> ForecastOrchestrator.getWind(key, gameTime));

        float effectiveSpeed = Math.max(wind.baseSpeed(), wind.gustSpeed());
        if (effectiveSpeed < 0.25f) {
            return Vec3.ZERO;
        }

        Vec3 direction = new Vec3(-Math.sin(wind.angleRadians()), 0.0, Math.cos(wind.angleRadians()));
        if (direction.lengthSqr() < 1.0E-6) {
            return Vec3.ZERO;
        }

        if (isUpwindBlocked(level, position, direction)) {
            return Vec3.ZERO;
        }

        double speedBlocksPerTick = effectiveSpeed * MPS_TO_BLOCKS_PER_TICK;
        return direction.normalize().scale(speedBlocksPerTick);
    }

    private static boolean isUpwindBlocked(Level level, Vec3 position, Vec3 direction) {
        Vec3 upwind = position.subtract(direction.normalize().scale(LINE_OF_SIGHT_CHECK));
        ClipContext context = new ClipContext(upwind, position, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        return level.clip(context).getType() != HitResult.Type.MISS;
    }
}

package net.Gabou.projectatmosphere.tornado;

import net.Gabou.projectatmosphere.api.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class TornadoSpawner {
    private TornadoSpawner() {}

    public static void spawn(BiomeInstanceKey key, ServerLevel level, float intensity) {
        BlockPos center = pickSpawnPosNear(key, level, TornadoConfig.BASE_SPAWN_RADIUS_M);
        WindVector.WindSample wind = WindVector.getSurface(key, level);
        float radius = 5f + 20f * intensity;
        net.Gabou.projectatmosphere.modules.core.WindVector w =
                net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(wind.speedMps(),
                        (float) Math.toRadians(wind.directionDeg()));
        TornadoManager.spawnServer(level, Vec3.atCenterOf(center), radius, w);
    }

    private static BlockPos pickSpawnPosNear(BiomeInstanceKey key, ServerLevel level, float radius) {
        BlockPos base = key.samplePos();
        int r = (int) radius;
        int dx = level.random.nextInt(-r, r + 1);
        int dz = level.random.nextInt(-r, r + 1);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                base.getX() + dx, base.getZ() + dz);
        return new BlockPos(base.getX() + dx, y, base.getZ() + dz);
    }
}


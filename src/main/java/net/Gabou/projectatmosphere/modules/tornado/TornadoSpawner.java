package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class TornadoSpawner {
    private TornadoSpawner() {}

    public static void spawn(BiomeInstanceKey key, ServerLevel level, float intensity) {
        float radiusSetting = AtmoCommonConfig.TORNADO_BASE_SPAWN_RADIUS_M.get().floatValue();
        BlockPos center = pickSpawnPosNear(key, level, radiusSetting);
        WindVectorApi.WindSample wind = WindVectorApi.getSurface(key);
        float radius = 5f + 20f * intensity;
        net.Gabou.projectatmosphere.modules.core.WindVector w =
                net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(wind.speedMps(),
                        (float) Math.toRadians(wind.directionDeg()));
        TornadoManager.spawnServer(level, Vec3.atCenterOf(center), radius, w);
    }

    public static void spawn(RegionInstanceKey key, ServerLevel level, float intensity) {
        float radiusSetting = AtmoCommonConfig.TORNADO_BASE_SPAWN_RADIUS_M.get().floatValue();
        BlockPos center = pickSpawnPosNear(key, level, radiusSetting);
        WindVectorApi.WindSample wind = WindVectorApi.getSurface(key, level.getGameTime());
        float radius = 5f + 20f * intensity;
        net.Gabou.projectatmosphere.modules.core.WindVector w =
                net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(wind.speedMps(),
                        (float) Math.toRadians(wind.directionDeg()));
        TornadoManager.spawnServer(level, Vec3.atCenterOf(center), radius, w);
    }

    private static BlockPos pickSpawnPosNear(BiomeInstanceKey key, ServerLevel level, float radius) {
        BlockPos base = key.samplePos();
        return pickSpawnPosNear(base, level, radius);
    }

    private static BlockPos pickSpawnPosNear(RegionInstanceKey key, ServerLevel level, float radius) {
        return pickSpawnPosNear(key.center(), level, radius);
    }

    private static BlockPos pickSpawnPosNear(BlockPos base, ServerLevel level, float radius) {
        int r = (int) radius;
        RandomSource random = RandomSource.create();
        int dx = random.nextInt(-r, r + 1);
        int dz = random.nextInt(-r, r + 1);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                base.getX() + dx, base.getZ() + dz);
        return new BlockPos(base.getX() + dx, y, base.getZ() + dz);
    }
}


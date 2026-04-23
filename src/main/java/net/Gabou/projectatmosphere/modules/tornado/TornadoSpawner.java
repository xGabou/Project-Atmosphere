package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.weather.StormSeverityScale;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class TornadoSpawner {
    private TornadoSpawner() {}

    public static void spawn(BiomeInstanceKey key, ServerLevel level, float intensity) {
        float radiusSetting = AtmoCommonConfig.TORNADO_BASE_SPAWN_RADIUS_M.get().floatValue();
        BlockPos center = pickSpawnPosNear(key, level, radiusSetting);
        WindVectorApi.WindSample wind = WindVectorApi.getSurface(key);
        int stormLevel = StormSeverityScale.resolve(level, net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry.resolveRegionKey(key), level.getGameTime());
        float radius = 5f + 18f * intensity + stormLevel * 1.5F;
        net.Gabou.projectatmosphere.modules.core.WindVector w =
                net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(wind.speedMps(),
                        (float) Math.toRadians(wind.directionDeg()));
        TornadoManager.spawnServer(level, Vec3.atCenterOf(center), radius, w, stormLevel);
    }

    public static void spawn(RegionInstanceKey key, ServerLevel level, float intensity) {
        float radiusSetting = AtmoCommonConfig.TORNADO_BASE_SPAWN_RADIUS_M.get().floatValue();
        BlockPos center = pickSpawnPosNear(key, level, radiusSetting);
        WindVectorApi.WindSample wind = WindVectorApi.getSurface(key, level.getGameTime());
        int stormLevel = StormSeverityScale.resolve(level, key, level.getGameTime());
        float radius = 5f + 18f * intensity + stormLevel * 1.5F;
        net.Gabou.projectatmosphere.modules.core.WindVector w =
                net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(wind.speedMps(),
                        (float) Math.toRadians(wind.directionDeg()));
        TornadoManager.spawnServer(level, Vec3.atCenterOf(center), radius, w, stormLevel);
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
        BlockPos best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = random.nextInt(-r, r + 1);
            int dz = random.nextInt(-r, r + 1);
            int x = base.getX() + dx;
            int z = base.getZ() + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos sample = new BlockPos(x, y, z);
            float score = scoreSpawnSurface(level, sample);
            if (score > bestScore) {
                bestScore = score;
                best = sample;
            }
        }
        return best == null ? base : best;
    }

    private static float scoreSpawnSurface(ServerLevel level, BlockPos pos) {
        BlockPos groundPos = pos.below();
        BlockState ground = level.getBlockState(groundPos);
        BlockState above = level.getBlockState(pos);
        float score = 1.0F;
        if (!ground.getFluidState().isEmpty()
                || ground.is(Blocks.WATER)
                || above.is(Blocks.WATER)
                || ground.getFluidState().is(FluidTags.WATER)
                || above.getFluidState().is(FluidTags.WATER)) {
            score -= 8.0F;
        }
        if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.PODZOL)) {
            score += 0.45F;
        }
        if (ground.is(Blocks.SAND) || ground.is(Blocks.RED_SAND)) {
            score -= 0.20F;
        }
        score -= sampleNearbyWaterPenalty(level, pos);
        return score;
    }

    private static float sampleNearbyWaterPenalty(ServerLevel level, BlockPos pos) {
        int[][] offsets = {
                {0, 0},
                {8, 0},
                {-8, 0},
                {0, 8},
                {0, -8},
                {8, 8},
                {8, -8},
                {-8, 8},
                {-8, -8}
        };
        int waterSamples = 0;
        for (int[] offset : offsets) {
            int x = pos.getX() + offset[0];
            int z = pos.getZ() + offset[1];
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos sample = new BlockPos(x, y - 1, z);
            BlockState state = level.getBlockState(sample);
            if (state.is(Blocks.WATER) || state.getFluidState().is(FluidTags.WATER)) {
                waterSamples++;
            }
        }
        return waterSamples / (float) offsets.length * 5.0F;
    }
}


package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.CloudWeatherSample;
import net.Gabou.projectatmosphere.clouds.WeatherCloudQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class LocalizedPrecipitationBlockUpdater {
    private LocalizedPrecipitationBlockUpdater() {
    }

    public static boolean shouldUseVanillaCompatibility(ServerLevel level) {
        return AtmosphereCloudPolicy.shouldOwnWeather(level);
    }

    public static void tickChunk(ServerLevel level, LevelChunk chunk) {
        if (level == null || chunk == null || !shouldUseVanillaCompatibility(level)) {
            return;
        }
        if (level.random.nextInt(16) != 0) {
            return;
        }

        int blockX = chunk.getPos().getMinBlockX();
        int blockZ = chunk.getPos().getMinBlockZ();
        BlockPos checkPos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING,
                level.getBlockRandomPos(blockX, 0, blockZ, 15)
        );
        CloudWeatherSample sample = WeatherCloudQueries.sampleAt(level, checkPos, true);
        if (!sample.hasRain()) {
            return;
        }

        BlockPos belowPos = checkPos.below();
        Biome biome = level.getBiome(belowPos).value();
        Biome.Precipitation precipitation = biome.getPrecipitationAt(belowPos);
        if (precipitation != Biome.Precipitation.NONE) {
            BlockState belowState = level.getBlockState(belowPos);
            belowState.getBlock().handlePrecipitation(belowState, level, belowPos, precipitation);
        }

        int snowAccumulationHeight = level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
        if (snowAccumulationHeight <= 0 || !sample.snowing() || !biome.shouldSnow(level, checkPos)) {
            return;
        }

        BlockState snowState = level.getBlockState(checkPos);
        if (snowState.is(Blocks.SNOW)) {
            int layers = snowState.getValue(SnowLayerBlock.LAYERS);
            if (layers < Math.min(snowAccumulationHeight, 8)) {
                BlockState updatedState = snowState.setValue(SnowLayerBlock.LAYERS, layers + 1);
                Block.pushEntitiesUp(snowState, updatedState, level, checkPos);
                level.setBlockAndUpdate(checkPos, updatedState);
            }
            return;
        }

        level.setBlockAndUpdate(checkPos, Blocks.SNOW.defaultBlockState());
    }
}

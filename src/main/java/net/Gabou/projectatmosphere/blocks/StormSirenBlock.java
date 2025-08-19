package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.WeatherSampler;
import net.Gabou.projectatmosphere.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Block that acts as a storm siren. Periodically samples the surrounding
 * biomes for storm intensity and plays a warning sound when a dangerous
 * storm is nearby.
 */
public class StormSirenBlock extends Block {
    private static final int CHECK_RADIUS = 400;
    private static final float INTENSITY_THRESHOLD = 6.0f;

    public StormSirenBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Set<BiomeInstanceKey> keys = WeatherSampler.sampleBiomesInArea(pos.getX(), pos.getZ(), CHECK_RADIUS, level);
        WeatherSampler.WeatherStats stats = WeatherSampler.computeWeatherStats(keys, level, level.getGameTime());
        if (stats != null && stats.stormChance() > INTENSITY_THRESHOLD) {
            level.playSound(null, pos, ModSounds.WEATHER_SIREN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        level.scheduleTick(pos, this, 20);
    }
}


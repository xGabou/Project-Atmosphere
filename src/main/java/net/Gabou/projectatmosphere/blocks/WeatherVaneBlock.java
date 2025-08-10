package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.InstrumentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block representing a weather vane that aligns with the wind direction.
 */
public class WeatherVaneBlock extends InstrumentBlock {

    /**
     * Creates a new weather vane block.
     *
     * @param properties block properties
     */
    public WeatherVaneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * Schedules the first tick to update the vane orientation when placed.
     *
     * @param state     block state
     * @param level     world level
     * @param pos       block position
     * @param oldState  previous block state
     * @param isMoving  whether the block is moving
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /**
     * Periodically aligns the vane with the current wind direction.
     *
     * @param state  block state
     * @param level  server level
     * @param pos    block position
     * @param random random source
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, level), pos);
        WindVector wind = ForecastOrchestrator.getCurrentWind(key, level.getGameTime());
        Direction dir = Direction.fromYRot((float) Math.toDegrees(wind.angleRadians())).getOpposite();
        if (dir != state.getValue(FACING)) {
            level.setBlock(pos, state.setValue(FACING, dir), 2);
        }
        level.scheduleTick(pos, this, 20);
    }

    /**
     * Displays the current wind information to the player.
     *
     * @param level  world level
     * @param player player receiving the data
     */
    @Override
    public void display(Level level, Player player) {
        InstrumentUtils.displayWind(level, player);
    }
}

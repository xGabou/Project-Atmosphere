package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.modules.weather.StormShieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class StormShieldBlock extends Block {
    public StormShieldBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            StormShieldManager.register(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            StormShieldManager.unregister(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

package net.Gabou.projectatmosphere.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Base class for interactive weather instruments that display data when used.
 */
public abstract class InstrumentBlock extends HorizontalDirectionalBlock implements InstrumentReader {

    /**
     * Creates a new instrument block with a default north-facing orientation.
     *
     * @param properties block properties
     */
    public InstrumentBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * Adds the facing property to the block state definition.
     *
     * @param builder state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Determines the block's orientation when placed by a player.
     *
     * @param context placement context
     * @return block state with facing opposite the player's direction
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Handles player interaction by displaying instrument data.
     *
     * @param state  block state
     * @param level  world level
     * @param pos    block position
     * @param player interacting player
     * @param hit    hit result
     * @return interaction result
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            display(level, player);
        }
        return InteractionResult.SUCCESS;
    }


}

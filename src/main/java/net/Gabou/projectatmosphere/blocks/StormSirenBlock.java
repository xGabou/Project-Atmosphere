package net.Gabou.projectatmosphere.blocks;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.WeatherSampler;
import net.Gabou.projectatmosphere.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Block that acts as a storm siren. Periodically samples the surrounding
 * biomes for storm intensity and plays a warning sound when a dangerous
 * storm is nearby.
 */
public class StormSirenBlock extends Block {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final int CHECK_RADIUS = 400;
    private static final float INTENSITY_THRESHOLD = 6.0f;

    public StormSirenBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();

        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return this.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER);
        }

        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(
                pos.above(),
                this.defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER),
                3
        );
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        DoubleBlockHalf half = state.getValue(HALF);

        if (half == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);

            if (belowState.getBlock() == this) {
                level.destroyBlock(below, !player.isCreative());
            }

        } else {
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);

            if (aboveState.getBlock() == this) {
                level.destroyBlock(above, false);
            }
        }

        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            level.scheduleTick(pos, this, 200);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return;
        }

        List<CloudRegion> lst = CloudManager.get(level).getClouds().stream()
                .filter(cloudRegion -> CloudLibrary.getSeverityFromRessourceLocation(cloudRegion.getCloudTypeId()) >= 6)
                .toList();

        if (!lst.isEmpty() && lst.stream().anyMatch(cloudRegion -> {
            double dx = cloudRegion.getWorldX() - pos.getX();
            double dz = cloudRegion.getWorldZ() - pos.getZ();
            return dx * dx + dz * dz < CHECK_RADIUS * CHECK_RADIUS;
        })) {
            level.playSound(null, pos, ModSounds.WEATHER_SIREN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }

        level.scheduleTick(pos, this, 200);
    }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }



}



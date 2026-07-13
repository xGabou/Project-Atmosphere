package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.clouds.simulation.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
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
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloc de sirène météo qui surveille les tornades et les régions de nuages PA proches.
 */
public class StormSirenBlock extends Block {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final int STORM_WARNING_RADIUS = 500;
    private static final int TORNADO_WARNING_RADIUS = 500;
    private static final int TORNADO_SOUND_INTERVAL_TICKS = 40;
    private static final float INTENSITY_THRESHOLD = 7.0f;
    private static final Map<Long, Boolean> STORM_ACTIVE = new ConcurrentHashMap<>();
    private static final Map<Long, Long> TORNADO_LAST_SOUND = new ConcurrentHashMap<>();

    public StormSirenBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        super.onBlockExploded(state, level, pos, explosion);
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
            level.scheduleTick(pos, this, CHECK_INTERVAL_TICKS);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return;
        }

        long now = level.getGameTime();
        long posKey = pos.asLong();

        if (isTornadoNearby(level, pos)) {
            long lastSound = TORNADO_LAST_SOUND.getOrDefault(posKey, Long.valueOf(TORNADO_SOUND_INTERVAL_TICKS));
            if (now - lastSound >= TORNADO_SOUND_INTERVAL_TICKS) {
                level.playSound(null, pos, ModSounds.WEATHER_SIREN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                TORNADO_LAST_SOUND.put(posKey, now);
            }
        } else {
            TORNADO_LAST_SOUND.remove(posKey);
        }

        boolean severeStorm = isSevereStormNearby(level, pos);
        boolean wasSevere = STORM_ACTIVE.getOrDefault(posKey, false);
        if (severeStorm && !wasSevere) {
            level.playSound(null, pos, ModSounds.WEATHER_SIREN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            STORM_ACTIVE.put(posKey, true);
        } else if (!severeStorm && wasSevere) {
            STORM_ACTIVE.remove(posKey);
        }

        level.scheduleTick(pos, this, CHECK_INTERVAL_TICKS);
    }

    private static boolean isTornadoNearby(ServerLevel level, BlockPos pos) {
        return AtmosphereCloudServices.get().hasActiveTornadoNear(level, pos, TORNADO_WARNING_RADIUS);
    }

    private static boolean isSevereStormNearby(ServerLevel level, BlockPos pos) {
        double radiusSq = STORM_WARNING_RADIUS * STORM_WARNING_RADIUS;
        return CloudRegionManager.getInstance().getActiveRenderData(level).stream().anyMatch(region -> {
            if (!isSevereRegion(region)) {
                return false;
            }
            double dx = region.getCenter().x() - pos.getX();
            double dz = region.getCenter().z() - pos.getZ();
            return dx * dx + dz * dz <= radiusSq;
        });
    }

    private static boolean isSevereRegion(CloudRegionRenderData region) {
        if (region == null || !region.isActive()) {
            return false;
        }
        float intensity = region.getDensity() * 6.0F + region.getCoverage() * 4.0F;
        return intensity >= INTENSITY_THRESHOLD;
    }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }



}



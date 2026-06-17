package net.Gabou.projectatmosphere.modules.seasonaltrees.integration;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.TreeRegistry;
import com.ferreusveritas.dynamictrees.tree.species.Species;
import com.ferreusveritas.dynamictrees.util.SafeChunkBounds;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.LeafState;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeasonPhase;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeKey;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeRecord;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeState;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.ModList;

public class DynamicTreesAccessor implements SeasonalTreesTreeAccessor {
    private static final float MIN_TRANSITION_PROGRESS = 0.1f;

    @Override
    public boolean isEnabled() {
        return AtmoCommonConfig.SEASONAL_TREES_DYNAMIC_TREES_ENABLED.get() && ModList.get().isLoaded("dynamictrees");
    }

    @Override
    public boolean isTreeValid(ServerLevel level, TreeKey key) {
        BlockState state = level.getBlockState(key.rootPos());
        return TreeHelper.getRootyOpt(state).isPresent();
    }

    @Override
    public BlockPos findRootInColumn(ServerLevel level, ChunkAccess chunk, int localX, int localZ) {
        int worldX = chunk.getPos().getBlockX(localX);
        int worldZ = chunk.getPos().getBlockZ(localZ);
        int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ);
        BlockPos surface = new BlockPos(worldX, surfaceY, worldZ);
        for (int dy = 0; dy <= 6; dy++) {
            BlockPos candidate = surface.below(dy);
            BlockState state = level.getBlockState(candidate);
            if (TreeHelper.getRootyOpt(state).isPresent()) {
                BlockPos root = TreeHelper.findRootNode(level, candidate);
                return root == null ? candidate : root;
            }
            if (TreeHelper.getBranch(state) != null) {
                BlockPos root = TreeHelper.findRootNode(level, candidate);
                if (root != null) {
                    return root;
                }
            }
        }
        return null;
    }

    @Override
    public TreeRecord createRecord(ServerLevel level, BlockPos rootPos) {
        ChunkPos chunkPos = new ChunkPos(rootPos);
        TreeKey key = new TreeKey(level.dimension().location(), chunkPos, rootPos, TreeType.DYNAMIC);
        return new TreeRecord(key, TreeState.defaultState());
    }

    @Override
    public void applyLeafState(ServerLevel level, TreeRecord record, SeasonPhase phase) {
        if (!isEnabled()) {
            return;
        }
        TreeState state = record.state();
        BlockPos rootPos = record.key().rootPos();
        if (state.leafState().isDormant()) {
            DynamicTreesDormancyHelper.applyDormantLeaves(level, rootPos, SafeChunkBounds.ANY);
            return;
        }
        if (state.leafState() == LeafState.FULL) {
            DynamicTreesDormancyHelper.applyActiveLeaves(level, rootPos, SafeChunkBounds.ANY);
            if (phase == SeasonPhase.SPRING || phase == SeasonPhase.SUMMER) {
                TreeHelper.growPulse(level, rootPos);
            }
            return;
        }
        if (phase == SeasonPhase.AUTUMN) {
            float progress = state.progress();
            if (state.leafState() == LeafState.PARTIAL && progress <= 0.0f) {
                progress = MIN_TRANSITION_PROGRESS;
            }
            DynamicTreesDormancyHelper.applyDormantLeaves(level, rootPos, SafeChunkBounds.ANY, progress);
        } else if (phase == SeasonPhase.SPRING) {
            float progress = state.progress();
            if (state.leafState() == LeafState.PARTIAL && progress <= 0.0f) {
                progress = MIN_TRANSITION_PROGRESS;
            }
            DynamicTreesDormancyHelper.applyActiveLeaves(level, rootPos, SafeChunkBounds.ANY, progress);
            TreeHelper.growPulse(level, rootPos);
        }
    }

    @Override
    public boolean isMature(ServerLevel level, TreeRecord record) {
        if (!isEnabled()) {
            return false;
        }
        int radius = TreeHelper.getRadius(level, record.key().rootPos());
        return radius >= 3;
    }

    @Override
    public ResourceLocation getSpeciesId(ServerLevel level, BlockPos rootPos) {
        if (!isEnabled()) {
            return null;
        }
        Species species = TreeHelper.getCommonSpecies(level, rootPos);
        if (species == null) {
            return null;
        }
        return species.getRegistryName();
    }

    @Override
    public boolean plantSeed(ServerLevel level, BlockPos pos, ResourceLocation speciesId) {
        if (!isEnabled() || speciesId == null) {
            return false;
        }
        Species species = TreeRegistry.findSpecies(speciesId);
        if (species == null || species == Species.NULL_SPECIES) {
            return false;
        }
        return species.plantSapling(level, pos, false);
    }
}

package net.Gabou.projectatmosphere.modules.seasonaltrees.integration;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeasonPhase;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeKey;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

public class VanillaTreesAccessor implements SeasonalTreesTreeAccessor {
    @Override
    public boolean isEnabled() {
        return AtmoCommonConfig.SEASONAL_TREES_VANILLA_ENABLED.get();
    }

    @Override
    public boolean isTreeValid(ServerLevel level, TreeKey key) {
        return false;
    }

    @Override
    public BlockPos findRootInColumn(ServerLevel level, ChunkAccess chunk, int localX, int localZ) {
        return null;
    }

    @Override
    public TreeRecord createRecord(ServerLevel level, BlockPos rootPos) {
        return null;
    }

    @Override
    public void applyLeafState(ServerLevel level, TreeRecord record, SeasonPhase phase) {
        // TODO: integrate conservative vanilla leaf aging without aggressive block removal.
    }

    @Override
    public boolean isMature(ServerLevel level, TreeRecord record) {
        return false;
    }

    @Override
    public ResourceLocation getSpeciesId(ServerLevel level, BlockPos rootPos) {
        return null;
    }

    @Override
    public boolean plantSeed(ServerLevel level, BlockPos pos, ResourceLocation speciesId) {
        // TODO: implement vanilla sapling placement if enabled.
        return false;
    }
}

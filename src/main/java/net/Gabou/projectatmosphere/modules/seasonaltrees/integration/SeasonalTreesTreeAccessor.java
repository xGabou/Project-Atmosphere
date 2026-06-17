package net.Gabou.projectatmosphere.modules.seasonaltrees.integration;

import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeasonPhase;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeKey;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

public interface SeasonalTreesTreeAccessor {
    boolean isEnabled();

    boolean isTreeValid(ServerLevel level, TreeKey key);

    BlockPos findRootInColumn(ServerLevel level, ChunkAccess chunk, int localX, int localZ);

    TreeRecord createRecord(ServerLevel level, BlockPos rootPos);

    void applyLeafState(ServerLevel level, TreeRecord record, SeasonPhase phase);

    boolean isMature(ServerLevel level, TreeRecord record);

    ResourceLocation getSpeciesId(ServerLevel level, BlockPos rootPos);

    boolean plantSeed(ServerLevel level, BlockPos pos, ResourceLocation speciesId);
}

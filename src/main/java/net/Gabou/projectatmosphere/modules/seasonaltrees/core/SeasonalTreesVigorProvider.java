package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface SeasonalTreesVigorProvider {
    float getVigorMultiplier(ServerLevel level, BlockPos pos);
}

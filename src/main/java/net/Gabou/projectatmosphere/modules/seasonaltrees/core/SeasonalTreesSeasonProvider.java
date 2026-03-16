package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

import net.minecraft.world.level.Level;

public interface SeasonalTreesSeasonProvider {
    SeasonPhase getPhase(Level level);
    float getPhaseProgress(Level level);
}

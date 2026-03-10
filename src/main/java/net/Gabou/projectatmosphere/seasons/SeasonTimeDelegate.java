package net.Gabou.projectatmosphere.seasons;

import net.minecraft.world.level.Level;

public interface SeasonTimeDelegate {
    SeasonSnapshot snapshot(Level level);

    long seasonCycleTicks(Level level);

    long seasonDuration(Level level);

    long dayDuration(Level level);
}

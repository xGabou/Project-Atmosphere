package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

import net.Gabou.projectatmosphere.seasons.SeasonSnapshot;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.minecraft.world.level.Level;

public class SeasonalTreesPaSeasonProvider implements SeasonalTreesSeasonProvider {
    @Override
    public SeasonPhase getPhase(Level level) {
        SeasonStage stage = SeasonTimeHelper.stage(level);
        if (stage == null) {
            return SeasonPhase.SUMMER;
        }
        return switch (stage) {
            case SPRING -> SeasonPhase.SPRING;
            case SUMMER -> SeasonPhase.SUMMER;
            case AUTUMN -> SeasonPhase.AUTUMN;
            case WINTER -> SeasonPhase.WINTER;
            default ->  null;
        };
    }

    @Override
    public float getPhaseProgress(Level level) {
        SeasonSnapshot snapshot = SeasonTimeHelper.snapshot(level);
        return snapshot == null ? 0.0f : snapshot.progress();
    }
}

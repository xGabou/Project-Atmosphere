package net.Gabou.projectatmosphere.seasons;

import net.Gabou.projectatmosphere.event.SeasonTracker;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.SeasonHelper;

/**
 * Season delegate backed by Serene Seasons.
 */
public class SereneSeasonsSeasonDelegate implements SeasonTimeDelegate {

    public SereneSeasonsSeasonDelegate() {
        SeasonTracker.register();
    }


    @Override
    public SeasonSnapshot snapshot(Level level) {
        if (level == null) {
            return SeasonSnapshot.neutral();
        }
        var state = SeasonHelper.getSeasonState(level);
        SeasonStage stage = switch (state.getSeason()) {
            case SPRING -> SeasonStage.SPRING;
            case SUMMER -> SeasonStage.SUMMER;
            case AUTUMN -> SeasonStage.AUTUMN;
            case WINTER -> SeasonStage.WINTER;
        };
        float progress = state.getDay() / (float) state.getSeasonDuration();
        return new SeasonSnapshot(new net.minecraft.resources.ResourceLocation("sereneseasons", "season"),
                stage, progress, 0.0f);
    }

    @Override
    public long seasonCycleTicks(Level level) {
        return level == null ? 24000L * 4 : SeasonHelper.getSeasonState(level).getSeasonCycleTicks();
    }

    @Override
    public long seasonDuration(Level level) {
        return level == null ? 24000L : SeasonHelper.getSeasonState(level).getSeasonDuration();
    }

    @Override
    public long dayDuration(Level level) {
        return level == null ? 24000L : SeasonHelper.getSeasonState(level).getDayDuration();
    }
}

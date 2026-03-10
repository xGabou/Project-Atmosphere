package net.Gabou.projectatmosphere.seasons;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.event.SeasonTracker;
import net.Gabou.projectatmosphere.util.ICloudRegionId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.SeasonHelper;
import com.Gabou.sereneseasonsplus.api.SSPApi;

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
        return new SeasonSnapshot(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sereneseasons", "season"),
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


    public static void  handleRainStarted(ServerLevel level, CloudRegion cloudRegion) {
        SSPApi.getINSTANCE().onSimpleCloudsSpawned(level, ((ICloudRegionId) cloudRegion).projectatmosphere$getId());
    }

    public static void handleRainEnded(ServerLevel level, CloudRegion cloudRegion) {
        SSPApi.getINSTANCE().onCloudsDespawned(level, ((ICloudRegionId) cloudRegion).projectatmosphere$getId());
    }
}

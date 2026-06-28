package net.Gabou.projectatmosphere.seasons;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.util.ICloudRegionId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import com.Gabou.sereneseasonsplus.api.SSPApi;

/**
 * Season delegate backed by Serene Seasons.
 */
public class SereneSeasonsSeasonDelegate implements SeasonTimeDelegate {
    private static final String PROVIDER_ID = "sereneseasons";

    public SereneSeasonsSeasonDelegate() {
        SereneSeasonsEventBridge.register();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public SeasonSnapshot snapshot(Level level) {
        return snapshot(level, null);
    }

    @Override
    public SeasonSnapshot snapshot(Level level, BlockPos pos) {
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
        float progress = seasonProgress(state.getSeasonCycleTicks(), state.getSeasonDuration());
        SeasonMoistureStage moistureStage = moistureStage(level, pos);
        float moistureProgress = moistureStage == SeasonMoistureStage.NEUTRAL
                ? 0.0f
                : tropicalMoistureProgress(state.getTropicalSeason(), progress);
        return new SeasonSnapshot(new ResourceLocation("sereneseasons", "season"),
                stage,
                progress,
                SeasonClimateProfile.temperatureOffsetC(stage, progress),
                moistureStage,
                moistureProgress);
    }

    private static float seasonProgress(int cycleTicks, int seasonDuration) {
        int safeSeasonDuration = Math.max(1, seasonDuration);
        int ticksInSeason = Math.floorMod(cycleTicks, safeSeasonDuration);
        return Mth.clamp(ticksInSeason / (float) safeSeasonDuration, 0f, 1f);
    }

    private static SeasonMoistureStage moistureStage(Level level, BlockPos pos) {
        if (pos == null || !SeasonHelper.usesTropicalSeasons(level.getBiome(pos))) {
            return SeasonMoistureStage.NEUTRAL;
        }
        return switch (SeasonHelper.getSeasonState(level).getTropicalSeason()) {
            case EARLY_DRY, MID_DRY, LATE_DRY -> SeasonMoistureStage.DRY;
            case EARLY_WET, MID_WET, LATE_WET -> SeasonMoistureStage.WET;
        };
    }

    private static float tropicalMoistureProgress(Season.TropicalSeason tropicalSeason, float phaseProgress) {
        int phaseInMoistureSeason = switch (tropicalSeason) {
            case EARLY_DRY, EARLY_WET -> 0;
            case MID_DRY, MID_WET -> 1;
            case LATE_DRY, LATE_WET -> 2;
        };
        return Mth.clamp((phaseInMoistureSeason + Mth.clamp(phaseProgress, 0f, 1f)) / 3f, 0f, 1f);
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


    @Override
    public void onRainStarted(ServerLevel level, CloudRegion cloudRegion) {
        if (isSereneSeasonsPlusLoaded()) {
            SSPApi.getINSTANCE().onSimpleCloudsSpawned(level, ((ICloudRegionId) cloudRegion).projectatmosphere$getId());
        }
    }

    @Override
    public void onRainEnded(ServerLevel level, CloudRegion cloudRegion) {
        if (isSereneSeasonsPlusLoaded()) {
            SSPApi.getINSTANCE().onCloudsDespawned(level, ((ICloudRegionId) cloudRegion).projectatmosphere$getId());
        }
    }

    /**
     * Vérifie si Serene Seasons Plus est chargé avant d'appeler son API.
     *
     * @return true si l'intégration SSP peut s'exécuter
     */
    private static boolean isSereneSeasonsPlusLoaded() {
        return ModList.get().isLoaded("sereneseasonsplus");
    }
}

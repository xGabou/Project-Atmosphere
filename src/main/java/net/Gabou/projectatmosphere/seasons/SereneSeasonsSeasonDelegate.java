package net.Gabou.projectatmosphere.seasons;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.util.ICloudRegionId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
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
        return new SeasonSnapshot(new net.minecraft.resources.ResourceLocation("sereneseasons", "season"),
                stage, progress, SeasonClimateProfile.temperatureOffsetC(stage, progress));
    }

    private static float seasonProgress(int cycleTicks, int seasonDuration) {
        int safeSeasonDuration = Math.max(1, seasonDuration);
        int ticksInSeason = Math.floorMod(cycleTicks, safeSeasonDuration);
        return Mth.clamp(ticksInSeason / (float) safeSeasonDuration, 0f, 1f);
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

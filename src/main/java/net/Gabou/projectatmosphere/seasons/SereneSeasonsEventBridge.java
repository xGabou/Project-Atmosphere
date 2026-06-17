package net.Gabou.projectatmosphere.seasons;

import com.Gabou.sereneseasonsplus.util.EnvironmentHelper;
import glitchcore.event.EventManager;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonChangedEvent;

public final class SereneSeasonsEventBridge {
    private static final String SERENE_SEASONS_PLUS_MOD_ID = "sereneseasonsplus";
    private static boolean registered;

    private SereneSeasonsEventBridge() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        EventManager.addListener((SeasonChangedEvent.Standard event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                if (event.getNewSeason().getSeason() != event.getPrevSeason().getSeason()) {
                    AtmosphereManager.onSeasonChange(serverLevel);
                    Season.SubSeason oldSeason = event.getPrevSeason();
                    Season.SubSeason newSeason = event.getNewSeason();
                    if (newSeason != oldSeason && isSereneSeasonsPlusLoaded()) {
                        EnvironmentHelper.onSeasonChange(serverLevel, Math.abs(newSeason.ordinal() - oldSeason.ordinal()) != 1);
                    }
                }
            }
        });

        EventManager.addListener((SeasonChangedEvent.Tropical event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                if (event.getNewSeason() != event.getPrevSeason()) {
                    AtmosphereManager.onSeasonChange(serverLevel);
                    Season.TropicalSeason oldSeason = event.getPrevSeason();
                    Season.TropicalSeason newSeason = event.getNewSeason();
                    boolean skippedAdjacentSeason = Math.abs(newSeason.ordinal() - oldSeason.ordinal()) != 1;
                    if (newSeason != oldSeason && isSereneSeasonsPlusLoaded()) {
                        EnvironmentHelper.onSeasonChange(serverLevel, skippedAdjacentSeason);
                    }
                }
            }
        });
    }

    /**
     * Vérifie si Serene Seasons Plus est chargé avant d'appeler son helper d'environnement.
     *
     * @return true si le compat SSP est disponible
     */
    private static boolean isSereneSeasonsPlusLoaded() {
        return ModList.get().isLoaded(SERENE_SEASONS_PLUS_MOD_ID);
    }
}

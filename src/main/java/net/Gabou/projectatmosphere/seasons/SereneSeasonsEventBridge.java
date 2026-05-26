package net.Gabou.projectatmosphere.seasons;

import com.Gabou.sereneseasonsplus.util.EnvironmentHelper;
import glitchcore.event.EventManager;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.minecraft.server.level.ServerLevel;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonChangedEvent;

public final class SereneSeasonsEventBridge {
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
                    if (newSeason != oldSeason) {
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
                    if (newSeason != oldSeason) {
                        EnvironmentHelper.onSeasonChange(serverLevel, skippedAdjacentSeason);
                    }
                }
            }
        });
    }
}

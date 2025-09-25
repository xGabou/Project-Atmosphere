package net.Gabou.projectatmosphere.event;

import com.Gabou.sereneseasonsplus.util.EnvironmentHelper;
import glitchcore.event.EventManager;
import net.minecraft.server.level.ServerLevel;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonChangedEvent;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;

public class SeasonTracker {

    public static void register() {
        EventManager.addListener((SeasonChangedEvent.Standard event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                if (event.getNewSeason().getSeason() != event.getPrevSeason().getSeason()) {
                    AtmosphereManager.onSeasonChange(serverLevel);
                    Season.SubSeason oldSeason = event.getPrevSeason();
                    Season.SubSeason newSeason = event.getNewSeason();
                    if (newSeason != oldSeason) {
                        EnvironmentHelper.onSeasonChange(serverLevel,Math.abs(newSeason.ordinal() - oldSeason.ordinal()) != 1);
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
                    boolean b = Math.abs(newSeason.ordinal() - oldSeason.ordinal()) != 1;
                    if (newSeason != oldSeason) {
                        EnvironmentHelper.onSeasonChange(serverLevel, b);
                    }
                }
            }
        });
    }
}

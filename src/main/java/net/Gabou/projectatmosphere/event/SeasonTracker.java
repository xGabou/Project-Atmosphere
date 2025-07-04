package net.Gabou.projectatmosphere.event;

import glitchcore.event.EventManager;
import net.minecraft.server.level.ServerLevel;
import sereneseasons.api.season.SeasonChangedEvent;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;

public class SeasonTracker {

    public static void register() {
        EventManager.addListener((SeasonChangedEvent.Standard event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                if (event.getNewSeason().getSeason() != event.getPrevSeason().getSeason()) {
                    AtmosphereManager.onSeasonChange(serverLevel);
                }
            }
        });

        EventManager.addListener((SeasonChangedEvent.Tropical event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                if (event.getNewSeason() != event.getPrevSeason()) {
                    AtmosphereManager.onSeasonChange(serverLevel);
                }
            }
        });
    }
}

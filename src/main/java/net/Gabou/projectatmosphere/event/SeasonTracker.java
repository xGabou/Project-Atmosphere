package net.Gabou.projectatmosphere.event;

import net.Gabou.gaboulibs.util.CompatUtils;
import net.Gabou.gaboulibs.util.InitGuards;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.seasonaltrees.SeasonalTreesBootstrap;
import glitchcore.event.EventManager;
import net.minecraft.server.level.ServerLevel;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonChangedEvent;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;

public class SeasonTracker {

    private static final String INIT_KEY = "projectatmosphere:seasonal_trees";
    private static final String PASSP_HOST_PROPERTY = "gaboulibs.pasphost";
    private static final String SSP_MODID = "sereneseasonsplus";

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

    public static void registerSeasonalTrees() {
        if (!InitGuards.tryStart(INIT_KEY)) {
            return;
        }
        SeasonalTreesBootstrap.registerReadOnlyEnhancements();
        CompatUtils.PaSspHost electedHost = CompatUtils.getPaSspHost();
        String reason = buildHostReason();
        boolean shouldHost = CompatUtils.shouldThisModHostPaSspFeature(ProjectAtmosphere.MODID);
        ProjectAtmosphere.LOGGER.info(
                "Seasonal trees init: Project Atmosphere host={} (elected host: {}, reason: {})",
                shouldHost ? "yes" : "no",
                electedHost,
                reason
        );
        if (CompatUtils.isSereneSeasonsPlusLoaded()) {
            boolean sspShouldHost = CompatUtils.shouldThisModHostPaSspFeature(SSP_MODID);
            ProjectAtmosphere.LOGGER.info(
                    "Seasonal trees init: Serene Seasons Plus host={} (elected host: {}, reason: {})",
                    sspShouldHost ? "yes" : "no",
                    electedHost,
                    reason
            );
        }
        if (!shouldHost) {
            return;
        }
        SeasonalTreesBootstrap.initHost();
    }

    private static String buildHostReason() {
        String override = System.getProperty(PASSP_HOST_PROPERTY);
        if (override != null && !override.isBlank()) {
            return PASSP_HOST_PROPERTY + "=" + override.trim();
        }
        boolean paLoaded = CompatUtils.isProjectAtmosphereLoaded();
        boolean sspLoaded = CompatUtils.isSereneSeasonsPlusLoaded();
        if (paLoaded && sspLoaded) {
            return "both Project Atmosphere and Serene Seasons Plus detected";
        }
        if (paLoaded) {
            return "only Project Atmosphere detected";
        }
        if (sspLoaded) {
            return "only Serene Seasons Plus detected";
        }
        return "neither Project Atmosphere nor Serene Seasons Plus detected";
    }
}

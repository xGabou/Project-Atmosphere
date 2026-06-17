package net.Gabou.projectatmosphere.compat;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import org.apache.logging.log4j.Logger;

final class CompatInitLogger {
    private CompatInitLogger() {
    }

    static void logTemperatureMod(Logger logger, TemperatureMod mod) {
        switch (mod) {
            case LEGENDARY_SURVIVAL -> logger.info("Legendary Survival Overhaul loaded");
            case TOUGH_AS_NAILS -> logger.info("Tough As Nails loaded");
            case COLD_SWEAT -> logger.info("Cold Sweat loaded");
            case NONE -> logger.info("No temperature mod loaded, skipping compatibility setup.");
        }
    }

    static void logModuleState(Logger logger, String name, boolean loaded, String loadedMessage, String missingMessage) {
        logger.info(loaded ? loadedMessage : missingMessage);
    }

    static void logInitSummary(Logger logger) {
        logModuleState(logger,
                "Sand Storms",
                CompatModuleDetector.isSandStormsLoaded(),
                "Sand Storms mod loaded, enabling compatibility.",
                "Sand Storms mod not found.");
        logModuleState(logger,
                "Auroras",
                CompatModuleDetector.isAurorasLoaded(),
                "Auroras detected - enabling Project Atmosphere aurora bridge.",
                "Auroras mod not detected.");
        logModuleState(logger,
                "Rainbows",
                CompatModuleDetector.isRainbowsLoaded(),
                "Rainbows detected - enabling Project Atmosphere rainbow bridge.",
                "Rainbows mod not detected.");
        logModuleState(logger,
                "Tectonic",
                CompatModuleDetector.isTectonicLoaded(),
                "Tectonic detected - enabling refined ocean geometry.",
                "Tectonic mod not detected.");
        logModuleState(logger,
                "Continents",
                CompatModuleDetector.isContinentsLoaded(),
                "Continents detected - enabling refined shoreline geometry.",
                "Continents mod not detected.");
        logModuleState(logger,
                "Dynamic Trees",
                CompatModuleDetector.isDynamicTreesLoaded(),
                "Dynamic Trees detected - enabling seasonal tree integration.",
                "Dynamic Trees not detected.");
    }
}

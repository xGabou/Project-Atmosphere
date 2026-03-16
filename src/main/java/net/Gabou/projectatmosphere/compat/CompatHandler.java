package net.Gabou.projectatmosphere.compat;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.MODID;

public class CompatHandler {

    private CompatHandler() {}

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static boolean isSandStormsLoaded() {
        return ModList.get().isLoaded("sandstorm");
    }

    public static boolean isAurorasLoaded() {
        return ModList.get().isLoaded("auroras");
    }

    public static boolean isRainbowsLoaded() {
        return ModList.get().isLoaded("rainbows");
    }

    public static boolean isTectonicLoaded() {
        return ModList.get().isLoaded("tectonic");
    }

    public static boolean isContinentsLoaded() {
        return ModList.get().isLoaded("continents");
    }

    public static boolean isDynamicTreesLoaded() {
        return ModList.get().isLoaded("dynamictrees");
    }

    public static TemperatureMod getActiveTemperatureMod() {
        if (ModList.get().isLoaded("legendarysurvivaloverhaul")) {
            return TemperatureMod.LEGENDARY_SURVIVAL;
        } else if (ModList.get().isLoaded("toughasnails")) {
            return TemperatureMod.TOUGH_AS_NAILS;
        } else if (ModList.get().isLoaded("coldsweat")) {
            return TemperatureMod.COLD_SWEAT;
        }
        return TemperatureMod.NONE;
    }

    public static boolean isLegendarySurvivalLoaded() {
        return getActiveTemperatureMod() == TemperatureMod.LEGENDARY_SURVIVAL;
    }
    public static boolean isToughAsNailsLoaded() {
        return getActiveTemperatureMod() == TemperatureMod.TOUGH_AS_NAILS;
    }
    public static boolean isColdSweatLoaded() {
        return getActiveTemperatureMod() == TemperatureMod.COLD_SWEAT;
    }

    public static boolean isATemperatureModLoaded() {
        return getActiveTemperatureMod() != TemperatureMod.NONE;
    }

    public static void init() {
        TemperatureMod mod = getActiveTemperatureMod();
        if(!ProjectAtmosphere.DEBUG_MODE)
            return;

        switch (mod) {
            case LEGENDARY_SURVIVAL -> LOGGER.info("Legendary Survival Overhaul loaded");
            case TOUGH_AS_NAILS -> LOGGER.info("Tough As Nails loaded");
            case COLD_SWEAT -> LOGGER.info("Cold Sweat loaded");
            case NONE -> LOGGER.info("No temperature mod loaded, skipping compatibility setup.");
        }
        LOGGER.info(isSandStormsLoaded()
                ? "Sand Storms mod loaded, enabling compatibility."
                : "Sand Storms mod not found.");
        LOGGER.info(isAurorasLoaded()
                ? "Auroras detected – enabling seasonal aurora tuning."
                : "Auroras mod not detected.");
        LOGGER.info(isRainbowsLoaded()
                ? "Rainbows detected – enabling precipitation bridge."
                : "Rainbows mod not detected.");
        LOGGER.info(isTectonicLoaded()
                ? "Tectonic detected – enabling refined ocean geometry."
                : "Tectonic mod not detected.");
        LOGGER.info(isContinentsLoaded()
                ? "Continents detected – enabling refined shoreline geometry."
                : "Continents mod not detected.");
        LOGGER.info(isDynamicTreesLoaded()
                ? "Dynamic Trees detected - enabling seasonal tree integration."
                : "Dynamic Trees not detected.");
    }
}

package net.Gabou.projectatmosphere.compat;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.MODID;

public class CompatHandler {

    private CompatHandler() {}

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    // ---------------------------------------------------------------------
    // Mod detection
    // ---------------------------------------------------------------------

    public static boolean isSandStormsLoaded() {
        return CompatModuleDetector.isSandStormsLoaded();
    }

    public static boolean isAurorasLoaded() {
        return CompatModuleDetector.isAurorasLoaded();
    }

    public static boolean isRainbowsLoaded() {
        return CompatModuleDetector.isRainbowsLoaded();
    }

    public static boolean isTectonicLoaded() {
        return CompatModuleDetector.isTectonicLoaded();
    }

    public static boolean isContinentsLoaded() {
        return CompatModuleDetector.isContinentsLoaded();
    }

    public static boolean isDynamicTreesLoaded() {
        return CompatModuleDetector.isDynamicTreesLoaded();
    }

    public static TemperatureMod getActiveTemperatureMod() {
        return resolveActiveTemperatureMod();
    }

    public static boolean isLegendarySurvivalLoaded() {
        return isTemperatureModLoaded(TemperatureMod.LEGENDARY_SURVIVAL);
    }

    public static boolean isToughAsNailsLoaded() {
        return isTemperatureModLoaded(TemperatureMod.TOUGH_AS_NAILS);
    }

    public static boolean isColdSweatLoaded() {
        return isTemperatureModLoaded(TemperatureMod.COLD_SWEAT);
    }


    public static boolean isATemperatureModLoaded() {
        return getActiveTemperatureMod() != TemperatureMod.NONE;
    }

    public static void init() {
        TemperatureMod mod = getActiveTemperatureMod();
        if (!ProjectAtmosphere.DEBUG_MODE) {
            return;
        }

        CompatInitLogger.logTemperatureMod(LOGGER, mod);
        CompatInitLogger.logInitSummary(LOGGER);
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    private static TemperatureMod resolveActiveTemperatureMod() {
        if (ModList.get().isLoaded("legendarysurvivaloverhaul")) {
            return TemperatureMod.LEGENDARY_SURVIVAL;
        }
        if (ModList.get().isLoaded("toughasnails")) {
            return TemperatureMod.TOUGH_AS_NAILS;
        }
        if (ModList.get().isLoaded("coldsweat")) {
            return TemperatureMod.COLD_SWEAT;
        }
        return TemperatureMod.NONE;
    }

    private static boolean isTemperatureModLoaded(TemperatureMod expected) {
        return getActiveTemperatureMod() == expected;
    }
}

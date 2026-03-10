package net.Gabou.projectatmosphere.compat;

import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.MODID;

public class CompatHandler {

    private CompatHandler() {
    }

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static TemperatureMod getActiveTemperatureMod() {
        if (ModList.get().isLoaded("toughasnails")) {
            return TemperatureMod.TOUGH_AS_NAILS;
        } else if (ModList.get().isLoaded("coldsweat")) {
            return TemperatureMod.COLD_SWEAT;
        }
        return TemperatureMod.NONE;
    }

    public static boolean isSandStormsLoaded() {
        return ModList.get().isLoaded("sandstorm");
    }

    public static boolean isRainbowsLoaded() {
        // CurseForge project is "rainboows" but mod id may vary between forks.
        return ModList.get().isLoaded("rainboows") || ModList.get().isLoaded("rainbows");
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
        switch (mod) {
            case TOUGH_AS_NAILS -> LOGGER.info("Tough As Nails loaded");
            case COLD_SWEAT -> LOGGER.info("Cold Sweat loaded");
            case NONE -> LOGGER.info("No temperature mod loaded, skipping compatibility setup.");
        }
        String sandStormMsg = isSandStormsLoaded() ? "Sand Storms mod loaded, enabling compatibility." : "Sand Storms mod not found.";
        LOGGER.info(sandStormMsg);
    }
}

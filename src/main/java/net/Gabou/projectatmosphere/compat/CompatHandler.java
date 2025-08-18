package net.Gabou.projectatmosphere.compat;

import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.MODID;

public class CompatHandler {

    private CompatHandler() {
        
    }

    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final boolean isLegendaryModLoaded = ModList.get().isLoaded("legendarysurvivaloverhaul");

    public static final boolean isToughAsNailsLoaded = ModList.get().isLoaded("toughasnails");
    public static void init()
    {
        if (isLegendaryModLoaded) {
            LOGGER.info("Legendary mod loaded");
        }
        else if (isToughAsNailsLoaded) {
            LOGGER.info("Tough as nails mod loaded");
        }
        else{
            LOGGER.info("ToughAsNails or Legendary Survival Overhaul is not loaded, skipping compatibility setup.");
        }

    }

}

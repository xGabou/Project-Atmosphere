package net.Gabou.projectatmosphere.seasons;

import com.teamtea.eclipticseasons.api.EclipticSeasonsApi;
import com.teamtea.eclipticseasons.api.util.EclipticUtil;
import net.Gabou.projectatmospherefortfc.seasons.TfcSeasonDelegate;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Initializes the season delegate based on loaded mods. Defaults to Serene Seasons if present,
 * otherwise attempts the PA-for-TFC bridge, otherwise crashes with guidance.
 */
public final class SeasonBootstrap {
    private static final Logger LOGGER = LogManager.getLogger("ProjectAtmosphere/Seasons");
    private static final String SERENE_ID = "sereneseasons";
    private static final String PA_TFC_ID = "projectatmospherefortfc";
    private static final String ECLIPTIC_ID = "eclipticseasons";

    private SeasonBootstrap() {
    }

    public static void initOrCrash() {
        ModList mods = ModList.get();
        if (mods.isLoaded(SERENE_ID)) {
            LOGGER.info("Detected Serene Seasons; using SereneSeasonsSeasonDelegate.");
            SeasonTimeHelper.setDelegate(new SereneSeasonsSeasonDelegate());
            return;
        }
        if (mods.isLoaded(PA_TFC_ID)) {
            LOGGER.info("Detected Project Atmosphere for TFC; using placeholder delegate (replace with real provider when available).");
            SeasonTimeHelper.setDelegate(new TfcSeasonDelegate());
            return;
        }
        if(mods.isLoaded(ECLIPTIC_ID)) {
            LOGGER.info("Detected Ecliptic Seasons; using EclipticSeasonsSeasonDelegate.");
            SeasonTimeHelper.setDelegate(new EclipticSeasonsSeasonDelegate());
            return;
        }
        throw new IllegalStateException("""
Project Atmosphere requires a season provider. Install Serene Seasons or ProjectAtmosphereForTFC or Ecliptic Seasons (or remove Project Atmosphere).
""");
    }
}

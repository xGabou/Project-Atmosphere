package net.Gabou.projectatmosphere.seasons;

import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ComparableVersion;

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
    public static boolean isModVersionGreaterThan(String modId, String targetVersion) {
        return ModList.get()
                .getModContainerById(modId)
                .map(container -> {
                    String version = container.getModInfo().getVersion().toString();

                    return new ComparableVersion(version)
                            .compareTo(new ComparableVersion(targetVersion)) > 0;
                })
                .orElse(false);
    }

    public static void initOrCrash() {
        ModList mods = ModList.get();
        if (mods.isLoaded(ECLIPTIC_ID)) {
            LOGGER.info("Detected Ecliptic Seasons; using EclipticSeasonsSeasonDelegate.");
            installDelegate("net.Gabou.projectatmosphere.seasons.EclipticSeasonsSeasonDelegate");
            return;
        }
        if (mods.isLoaded(SERENE_ID)) {
            LOGGER.info("Detected Serene Seasons; using SereneSeasonsSeasonDelegate.");
            installDelegate("net.Gabou.projectatmosphere.seasons.SereneSeasonsSeasonDelegate");
            return;
        }
        if (mods.isLoaded(PA_TFC_ID)) {
            LOGGER.info("Detected Project Atmosphere for TFC; using placeholder delegate (replace with real provider when available).");
            if(!isModVersionGreaterThan(PA_TFC_ID, "1.1.0")) {
                LOGGER.warn("Pa 0.9.x need Pa x TFC version 1.2.0 or higher to work, please download it or wait for it to release. This was written on 19th June 2026. Pa x TFC 1.2.0 will be released either after the 1.0.0.0 PA's release or around it!");
            }
            installDelegate("net.Gabou.projectatmospherefortfc.seasons.TfcSeasonDelegate");
            return;
        }
        LOGGER.warn("No optional season provider detected; using Project Atmosphere's neutral season delegate.");
        SeasonTimeHelper.useNeutralDelegate();
    }

    private static void installDelegate(String className) {
        try {
            Class<?> delegateClass = Class.forName(className, true, SeasonBootstrap.class.getClassLoader());
            if (!SeasonTimeDelegate.class.isAssignableFrom(delegateClass)) {
                throw new IllegalStateException(className + " does not implement SeasonTimeDelegate");
            }
            SeasonTimeHelper.setDelegate((SeasonTimeDelegate) delegateClass.getConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.error("Season provider {} is present but its delegate failed to initialize; using neutral seasons.", className, exception);
            SeasonTimeHelper.useNeutralDelegate();
        }
    }
}

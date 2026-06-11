package net.Gabou.projectatmosphere.clouds.service;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraftforge.fml.ModList;

/**
 * Résout le service de nuages actif sans charger Simple Clouds quand il est absent.
 */
public final class AtmosphereCloudServices {

    private static final String SIMPLE_CLOUDS_MOD_ID = "simpleclouds";
    private static AtmosphereCloudService service;

    private AtmosphereCloudServices() {
    }

    /**
     * Retourne le service de nuages actif.
     *
     * @return service de nuages natif ou compat
     */
    public static AtmosphereCloudService get() {
        if (service == null) {
            service = createService();
        }

        return service;
    }

    /**
     * Indique si Simple Clouds est chargé.
     *
     * @return true si Simple Clouds est présent
     */
    public static boolean isSimpleCloudsLoaded() {
        return ModList.get().isLoaded(SIMPLE_CLOUDS_MOD_ID);
    }

    private static AtmosphereCloudService createService() {
        if (isSimpleCloudsLoaded()) {
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Simple Clouds detected; PA cloud module is disabled.");
            return new DisabledAtmosphereCloudService();
        }

        ProjectAtmosphere.LOGGER.info("[Atmosphere] Simple Clouds absent; using native PA cloud service.");
        return new NativeAtmosphereCloudService();
    }
}

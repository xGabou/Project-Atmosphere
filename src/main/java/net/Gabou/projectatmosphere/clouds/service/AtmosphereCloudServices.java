package net.Gabou.projectatmosphere.clouds.service;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsAtmosphereCloudService;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.neoforged.fml.ModList;

/**
 * RÃ©sout le service de nuages actif sans charger Simple Clouds quand il est absent.
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
     * Indique si Simple Clouds est chargÃ©.
     *
     * @return true si Simple Clouds est prÃ©sent
     */
    public static boolean isSimpleCloudsLoaded() {
        return ModList.get().isLoaded(SIMPLE_CLOUDS_MOD_ID);
    }

    private static AtmosphereCloudService createService() {
        if (AtmoCommonConfig.CLOUD_MODE.get() == AtmoCommonConfig.CloudMode.VANILLA) {
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud rendering disabled by config; using disabled cloud service.");
            return new DisabledAtmosphereCloudService();
        }
        if (isSimpleCloudsLoaded()) {
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Simple Clouds detected; using Simple Clouds cloud service.");
            return new SimpleCloudsAtmosphereCloudService();
        }

        ProjectAtmosphere.LOGGER.info("[Atmosphere] Simple Clouds absent; using native PA cloud service.");
        return new NativeAtmosphereCloudService();
    }
}

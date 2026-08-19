package net.Gabou.projectatmosphere.clouds.service;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.platform.config.AtmosphereConfig;
import net.Gabou.projectatmosphere.platform.AtmospherePlatform;

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
        return AtmospherePlatform.environment().isModLoaded(SIMPLE_CLOUDS_MOD_ID);
    }

    private static AtmosphereCloudService createService() {
        if (AtmosphereConfig.clouds().vanillaCloudMode()) {
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Cloud rendering disabled by config; using disabled cloud service.");
            return new DisabledAtmosphereCloudService();
        }
        if (isSimpleCloudsLoaded()) {
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Simple Clouds detected; using Simple Clouds cloud service.");
            return createOptionalService(
                    "net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsAtmosphereCloudService"
            );
        }

        ProjectAtmosphere.LOGGER.info("[Atmosphere] Simple Clouds absent; using native PA cloud service.");
        return new NativeAtmosphereCloudService();
    }

    private static AtmosphereCloudService createOptionalService(String className) {
        try {
            Class<?> serviceClass = Class.forName(className, true, AtmosphereCloudServices.class.getClassLoader());
            if (!AtmosphereCloudService.class.isAssignableFrom(serviceClass)) {
                throw new IllegalStateException(className + " does not implement AtmosphereCloudService");
            }
            return (AtmosphereCloudService) serviceClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError exception) {
            ProjectAtmosphere.LOGGER.error(
                    "[Atmosphere] Optional cloud backend {} failed to initialize; its visual renderer remains owner but PA integration is disabled.",
                    className,
                    exception
            );
            return new DisabledAtmosphereCloudService();
        }
    }
}

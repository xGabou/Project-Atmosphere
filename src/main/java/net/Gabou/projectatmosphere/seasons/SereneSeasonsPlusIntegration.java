package net.Gabou.projectatmosphere.seasons;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;

/** Safe facade around the optional Serene Seasons Plus integration. */
final class SereneSeasonsPlusIntegration {
    private static final String MOD_ID = "sereneseasonsplus";
    private static final String BRIDGE =
            "net.Gabou.projectatmosphere.compat.sereneseasonsplus.SereneSeasonsPlusBridge";

    private SereneSeasonsPlusIntegration() {
    }

    static void onCloudSpawned(ServerLevel level, int cloudRegionId) {
        invoke("onCloudSpawned", new Class<?>[]{ServerLevel.class, int.class}, level, cloudRegionId);
    }

    static void onCloudDespawned(ServerLevel level, int cloudRegionId) {
        invoke("onCloudDespawned", new Class<?>[]{ServerLevel.class, int.class}, level, cloudRegionId);
    }

    static void onSeasonChanged(ServerLevel level, boolean skippedAdjacentSeason) {
        invoke("onSeasonChanged", new Class<?>[]{ServerLevel.class, boolean.class}, level, skippedAdjacentSeason);
    }

    private static void invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class<?> bridge = Class.forName(BRIDGE, true, SereneSeasonsPlusIntegration.class.getClassLoader());
            bridge.getMethod(methodName, parameterTypes).invoke(null, arguments);
        } catch (ReflectiveOperationException | LinkageError exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause()
                    : exception;
            ProjectAtmosphere.LOGGER.error("Serene Seasons Plus bridge method {} failed", methodName, cause);
        }
    }
}

package net.Gabou.projectatmosphere.clouds.backend;

import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.platform.config.AtmosphereConfig;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class CloudBackendResolver {
    private CloudBackendResolver() {
    }

    public static CloudVisualBackend resolve(@Nullable Level level) {
        if (AtmosphereConfig.clouds().vanillaCloudMode()
                || !AtmosphereCloudPolicy.canUsePaInDimension(level)) {
            return CloudVisualBackend.DISABLED;
        }
        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return CloudVisualBackend.SIMPLE_CLOUDS;
        }
        return CloudVisualBackend.PA_NATIVE;
    }
}

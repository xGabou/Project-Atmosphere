package net.Gabou.projectatmosphere.clouds.backend;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Accès interne aux données persistantes des régions de nuage.
 * Le code externe doit passer par CloudRegionManager.
 */
final class CloudRegionBackend {

    private CloudRegionBackend() {

    }

    static @NotNull CloudRegionRegistry getRegistry(@NotNull ServerLevel level) {
        return CloudRegionSavedData.get(level).getRegistry();
    }

    static void markDirty(@NotNull ServerLevel level) {
        CloudRegionSavedData.get(level).markChanged();
    }
}
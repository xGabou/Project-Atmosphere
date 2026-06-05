package net.Gabou.projectatmosphere.clouds.backend;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Point d'entrée backend pour les régions de nuage Project Atmosphere.
 * Cette classe centralise l'accès au registre persistant.
 */
public final class CloudRegionBackend {

    private CloudRegionBackend() {

    }

    /**
     * Retourne le registre persistant des régions de nuage pour un niveau.
     *
     * @param level niveau serveur
     * @return registre persistant des régions de nuage
     */
    public static @NotNull CloudRegionRegistry getRegistry(@NotNull ServerLevel level) {
        return CloudRegionSavedData.get(level).getRegistry();
    }

    /**
     * Marque les données persistantes comme modifiées.
     *
     * @param level niveau serveur
     */
    public static void markDirty(@NotNull ServerLevel level) {
        CloudRegionSavedData.get(level).markChanged();
    }
}
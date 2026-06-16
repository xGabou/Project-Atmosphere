package net.Gabou.projectatmosphere.clouds.state;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

/**
 * Façade contrôlée vers la persistance des régions de nuage.
 * Le registre réel reste interne au package state.
 */
public final class CloudRegionStateStore {

    private CloudRegionStateStore() {

    }

    /**
     * Ajoute une région au stockage persistant du niveau.
     *
     * @param level niveau serveur
     * @param state état de région à stocker
     */
    public static void add(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        CloudRegionBackend.getRegistry(level).add(state);
        markDirty(level);
    }

    /**
     * Supprime une région du stockage persistant du niveau.
     *
     * @param level niveau serveur
     * @param regionId identifiant de région
     */
    public static void remove(@NotNull ServerLevel level, @NotNull UUID regionId) {
        CloudRegionBackend.getRegistry(level).remove(regionId);
        markDirty(level);
    }

    /**
     * Supprime toutes les régions stockées pour le niveau.
     *
     * @param level niveau serveur
     */
    public static void clear(@NotNull ServerLevel level) {
        CloudRegionBackend.getRegistry(level).clear();
        markDirty(level);
    }

    /**
     * Supprime les régions inactives.
     *
     * @param level niveau serveur
     * @return nombre de régions supprimées
     */
    public static int removeInactiveRegions(@NotNull ServerLevel level) {
        int removed = CloudRegionBackend.getRegistry(level).removeInactiveRegions();
        if (removed > 0) {
            markDirty(level);
        }
        return removed;
    }

    /**
     * Retourne toutes les régions stockées.
     *
     * @param level niveau serveur
     * @return états sauvegardés
     */
    public static @NotNull Collection<CloudRegionState> getAll(@NotNull ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).getAll();
    }

    /**
     * Retourne les régions actives.
     *
     * @param level niveau serveur
     * @return états actifs
     */
    public static @NotNull Collection<CloudRegionState> getActiveRegions(@NotNull ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).getActiveRegions();
    }

    /**
     * Retourne les données transportables des régions actives.
     *
     * @param level niveau serveur
     * @return données de rendu réseau
     */
    public static @NotNull Collection<CloudRegionRenderData> createRenderDataForActiveRegions(@NotNull ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).createRenderDataForActiveRegions(level.getGameTime());
    }

    /**
     * Retourne le nombre de régions stockées.
     *
     * @param level niveau serveur
     * @return nombre de régions
     */
    public static int size(@NotNull ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).size();
    }

    /**
     * Marque le stockage comme modifié.
     *
     * @param level niveau serveur
     */
    public static void markDirty(@NotNull ServerLevel level) {
        CloudRegionBackend.markDirty(level);
    }
}

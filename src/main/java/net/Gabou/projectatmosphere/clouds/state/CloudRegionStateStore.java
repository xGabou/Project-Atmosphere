package net.Gabou.projectatmosphere.clouds.state;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Façade contrôlée vers la persistance des régions de nuage.
 * Le registre réel reste interne au package state.
 */
public final class CloudRegionStateStore {
    private static volatile CloudRegionStateRepository repository;

    private CloudRegionStateStore() {

    }

    public static void install(CloudRegionStateRepository installedRepository) {
        repository = Objects.requireNonNull(installedRepository, "installedRepository");
    }

    private static CloudRegionStateRepository repository() {
        CloudRegionStateRepository current = repository;
        if (current == null) {
            throw new IllegalStateException("Cloud region state repository has not been installed");
        }
        return current;
    }

    /**
     * Ajoute une région au stockage persistant du niveau.
     *
     * @param level niveau serveur
     * @param state état de région à stocker
     */
    public static void add(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        repository().add(level, state);
    }

    /**
     * Supprime une région du stockage persistant du niveau.
     *
     * @param level niveau serveur
     * @param regionId identifiant de région
     */
    public static void remove(@NotNull ServerLevel level, @NotNull UUID regionId) {
        repository().remove(level, regionId);
    }

    /**
     * Supprime toutes les régions stockées pour le niveau.
     *
     * @param level niveau serveur
     */
    public static void clear(@NotNull ServerLevel level) {
        repository().clear(level);
    }

    /**
     * Supprime les régions inactives.
     *
     * @param level niveau serveur
     * @return nombre de régions supprimées
     */
    public static int removeInactiveRegions(@NotNull ServerLevel level) {
        return repository().removeInactiveRegions(level);
    }

    /**
     * Retourne toutes les régions stockées.
     *
     * @param level niveau serveur
     * @return états sauvegardés
     */
    public static @NotNull Collection<CloudRegionState> getAll(@NotNull ServerLevel level) {
        return repository().getAll(level);
    }

    /**
     * Retourne les régions actives.
     *
     * @param level niveau serveur
     * @return états actifs
     */
    public static @NotNull Collection<CloudRegionState> getActiveRegions(@NotNull ServerLevel level) {
        return repository().getActiveRegions(level);
    }

    /**
     * Retourne les données transportables des régions actives.
     *
     * @param level niveau serveur
     * @return données de rendu réseau
     */
    public static @NotNull Collection<CloudRegionRenderData> createRenderDataForActiveRegions(@NotNull ServerLevel level) {
        return repository().createRenderDataForActiveRegions(level);
    }

    /**
     * Retourne le nombre de régions stockées.
     *
     * @param level niveau serveur
     * @return nombre de régions
     */
    public static int size(@NotNull ServerLevel level) {
        return repository().size(level);
    }

    /**
     * Marque le stockage comme modifié.
     *
     * @param level niveau serveur
     */
    public static void markDirty(@NotNull ServerLevel level) {
        repository().markDirty(level);
    }
}

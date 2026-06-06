package net.Gabou.projectatmosphere.clouds.service;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Service d'intégration des systèmes de nuages utilisables par Project Atmosphere.
 * Le code principal doit dépendre de cette interface plutôt que d'un mod externe.
 */
public interface AtmosphereCloudService {

    /**
     * Initialise le service au démarrage serveur.
     *
     * @param level niveau serveur principal
     */
    default void onServerStarting(ServerLevel level) {
    }

    /**
     * Initialise le suivi des nuages au démarrage complet du serveur.
     *
     * @param level niveau serveur principal
     */
    default void onServerStarted(ServerLevel level) {
    }

    /**
     * Nettoie l'état du service à l'arrêt serveur.
     *
     * @param level niveau serveur principal
     */
    default void onServerStopping(ServerLevel level) {
    }

    /**
     * Nettoie les nuages contrôlés par le service pendant une régénération météo.
     *
     * @param level niveau serveur principal
     */
    default void clearForRegeneration(ServerLevel level) {
    }

    /**
     * Met à jour les données internes du service.
     *
     * @param level niveau serveur
     * @param tickCount compteur de tick PA
     */
    default void tick(ServerLevel level, int tickCount) {
    }

    /**
     * Indique si une tentative de spawn doit être lancée.
     *
     * @param level niveau serveur
     * @param cloudBoosterTicks accélérateur de spawn actuel
     * @param wasRegenerating true si une régénération vient de finir
     * @return true si le service veut tenter un spawn
     */
    default boolean shouldTrySpawn(ServerLevel level, int cloudBoosterTicks, boolean wasRegenerating) {
        return wasRegenerating;
    }

    /**
     * Tente de créer des nuages pour le service actif.
     *
     * @param level niveau serveur
     */
    default void trySpawnClouds(ServerLevel level) {
    }

    /**
     * Met à jour le compteur de boost de spawn après le tick.
     *
     * @param level niveau serveur
     * @param currentCloudBoosterTicks valeur actuelle
     * @return nouvelle valeur du boost
     */
    default int updateCloudBoosterTicks(ServerLevel level, int currentCloudBoosterTicks) {
        return currentCloudBoosterTicks;
    }

    /**
     * Applique les effets de débris liés aux nuages sévères.
     *
     * @param level niveau serveur
     */
    default void simulateSevereCloudDebris(ServerLevel level) {
    }

    /**
     * Force une couverture de nuage autour d'une position.
     *
     * @param pos position cible
     * @param level niveau serveur
     */
    default void ensureCloudAtPosition(BlockPos pos, ServerLevel level) {
    }

    /**
     * Indique si le service externe est disponible.
     *
     * @return true si le service externe est actif
     */
    default boolean isAvailable() {
        return true;
    }
}

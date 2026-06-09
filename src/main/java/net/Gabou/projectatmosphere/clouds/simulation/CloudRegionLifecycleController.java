package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Gère la durée de vie, la croissance et la disparition des régions de nuage backend.
 * Cette classe ne fait pas de rendu et ne synchronise rien directement.
 */
final class CloudRegionLifecycleController {

    private static final int DEFAULT_GROWTH_DURATION_TICKS = 20 * 30;
    private static final int DEFAULT_DECAY_DURATION_TICKS = 20 * 30;

    /**
     * Met à jour le cycle de vie d'une région de nuage.
     *
     * @param level niveau serveur
     * @param state région de nuage à mettre à jour
     * @return true si la région a été modifiée
     */
    boolean tick(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        if (!state.isActive() || state.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (CloudClusterState cluster : state.getClusters()) {
            if (cluster == null || !cluster.isActive()) {
                continue;
            }

            int nextAge = cluster.getAgeTicks() + 1;
            int lifetime = Math.max(1, cluster.getLifetimeTicks());

            cluster.setAgeTicks(nextAge);

            float growth = computeGrowth(nextAge);
            float decay = computeDecay(nextAge, lifetime);

            cluster.setGrowth(growth);
            cluster.setDecay(decay);

            if (nextAge >= lifetime) {
                cluster.setActive(false);
            }
            changed = true;
        }

        state.getClusters().stream()
                .filter(cluster -> cluster != null && !cluster.isActive())
                .forEach(cluster -> state.removeCluster(cluster));

        return changed;
    }

    /**
     * Calcule la croissance du nuage selon son âge.
     *
     * @param ageTicks âge actuel du nuage
     * @return facteur de croissance entre 0 et 1
     */
    private float computeGrowth(int ageTicks) {
        if (ageTicks <= 0) {
            return 0.0F;
        }

        if (ageTicks >= DEFAULT_GROWTH_DURATION_TICKS) {
            return 1.0F;
        }

        return (float) ageTicks / (float) DEFAULT_GROWTH_DURATION_TICKS;
    }

    /**
     * Calcule la disparition du nuage selon sa durée de vie restante.
     *
     * @param ageTicks âge actuel du nuage
     * @param lifetimeTicks durée de vie totale du nuage
     * @return facteur de disparition entre 0 et 1
     */
    private float computeDecay(int ageTicks, int lifetimeTicks) {
        int remainingTicks = lifetimeTicks - ageTicks;

        if (remainingTicks <= 0) {
            return 1.0F;
        }

        if (remainingTicks >= DEFAULT_DECAY_DURATION_TICKS) {
            return 0.0F;
        }

        return 1.0F - ((float) remainingTicks / (float) DEFAULT_DECAY_DURATION_TICKS);
    }
}

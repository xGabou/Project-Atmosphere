package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.minecraft.util.Mth;
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
            changed |= integrateGrowth(level, cluster, growth, decay);

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

    private boolean integrateGrowth(@NotNull ServerLevel level, @NotNull CloudClusterState cluster, float growth, float decay) {
        float beforeRadius = cluster.getRadius();
        float beforeCoverage = cluster.getCoverage();
        float beforeDensity = cluster.getDensity();

        float targetRadius = Math.min(CloudClusterState.RADIUS_CAP, Math.max(1.0F, cluster.getTargetRadius()));
        float desiredRadius = Mth.lerp(growth, cluster.getSpawnRadius(), targetRadius);
        if (targetRadius >= beforeRadius) {
            desiredRadius = Math.max(beforeRadius, desiredRadius);
        }
        desiredRadius = Math.min(CloudClusterState.RADIUS_CAP, Math.max(1.0F, desiredRadius));

        float targetCoverage = cluster.getTargetCoverage();
        float targetDensity = cluster.getTargetDensity();
        float desiredCoverage = Mth.lerp(0.08F, beforeCoverage, Mth.lerp(growth, beforeCoverage, targetCoverage));
        float desiredDensity = Mth.lerp(0.08F, beforeDensity, Mth.lerp(growth, beforeDensity, targetDensity));

        if (decay > 0.0F) {
            float fade = 1.0F - (decay * 0.65F);
            desiredCoverage = Mth.clamp(desiredCoverage * fade, 0.0F, 1.0F);
            desiredDensity = Mth.clamp(desiredDensity * fade, 0.0F, 1.0F);
        }

        float radiusDelta = desiredRadius - beforeRadius;
        boolean radiusChanged = Math.abs(radiusDelta) > 0.001F;
        boolean coverageChanged = Math.abs(desiredCoverage - beforeCoverage) > 0.0005F;
        boolean densityChanged = Math.abs(desiredDensity - beforeDensity) > 0.0005F;

        if (radiusChanged) {
            cluster.setRadius(desiredRadius);
        }
        if (coverageChanged) {
            cluster.setCoverage(desiredCoverage);
        }
        if (densityChanged) {
            cluster.setDensity(desiredDensity);
        }

        cluster.setLastGrowthRate(radiusDelta);
        if (radiusChanged || coverageChanged || densityChanged) {
            cluster.setLastGrowthTick(level.getGameTime());
            return true;
        }

        return false;
    }
}

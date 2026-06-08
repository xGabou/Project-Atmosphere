package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.type.CloudEvolutionTarget;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Contrôle l'évolution backend des types de nuages.
 * Cette classe ne rend rien et ne synchronise rien directement.
 */
final class CloudRegionEvolutionController {

    private static final int EVOLUTION_CHECK_INTERVAL_TICKS = 20;

    /**
     * Met à jour le type vivant d'une région de nuage.
     *
     * @param level niveau serveur
     * @param state état de région à faire évoluer
     * @return true si le type de nuage a changé
     */
    boolean tick(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        if (!state.isActive()) {
            return false;
        }

        state.incrementCloudTypeTicks();

        if (state.getCloudTypeTicks() % EVOLUTION_CHECK_INTERVAL_TICKS != 0) {
            return false;
        }

        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(state.getCloudTypeId());

        for (CloudEvolutionTarget target : definition.getEvolutionRules().getTargets()) {
            if (!target.matches(
                    state.getCloudTypeTicks(),
                    resolveHumidity(level, state),
                    resolveInstability(level, state),
                    resolvePressure(level, state),
                    resolveStormChance(level, state)
            )) {
                continue;
            }

            if (level.getRandom().nextFloat() > target.getChancePerCheck()) {
                continue;
            }

            state.changeCloudType(target.getTargetCloudTypeId());
            CloudRegionTypeGeometry.apply(state, state.getCloudTypeId());
            return true;
        }

        return false;
    }

    private float resolveHumidity(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        // TODO: Brancher l'humidité réelle de la région météo PA quand elle sera exposée proprement.
        return 0.80F;
    }

    private float resolveInstability(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        // TODO: Remplacer cette valeur par l'instabilité atmosphérique calculée par PA.
        return 0.75F;
    }

    private float resolvePressure(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        // TODO: Lire la pression de région au lieu de cette valeur de test sûre.
        return 0.93F;
    }

    private float resolveStormChance(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        // TODO: Brancher la vraie probabilité d'orage quand le backend météo la fournira.
        return 0.45F;
    }
}

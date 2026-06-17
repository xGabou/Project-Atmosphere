package net.Gabou.projectatmosphere.clouds.type;

import java.util.List;

/**
 * Liste immuable des évolutions possibles pour un type de nuage.
 */
public final class CloudEvolutionRules {

    private final List<CloudEvolutionTarget> targets;

    public CloudEvolutionRules(List<CloudEvolutionTarget> targets) {
        this.targets = targets != null ? List.copyOf(targets) : List.of();
    }

    /**
     * Retourne les cibles d'évolution possibles.
     *
     * @return cibles d'évolution
     */
    public List<CloudEvolutionTarget> getTargets() {
        return targets;
    }
}

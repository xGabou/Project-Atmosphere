package net.Gabou.projectatmosphere.clouds.frontend;

import org.jetbrains.annotations.NotNull;

/**
 * Prépare le futur rendu raymarch des nuages live.
 * Cette classe ne lit pas le backend et ne touche jamais au debugSnapshot.
 */
public final class CloudRaymarchRenderer {

    private CloudRaymarchRenderer() {

    }

    /**
     * Prépare le rendu d'un snapshot live.
     * Le vrai shader volumétrique sera branché ici plus tard.
     *
     * @param frameContext contexte de rendu de la frame courante
     * @param snapshot snapshot live valide
     */
    public static void renderSnapshot(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull CloudRenderSnapshot snapshot
    ) {
        float effectiveDensity = CloudDensityProvider.getEffectiveDensity(snapshot);
        float effectiveCoverage = CloudDensityProvider.getEffectiveCoverage(snapshot);

        if (effectiveDensity <= 0.001F || effectiveCoverage <= 0.001F) {
            return;
        }

        CloudRenderProfile profile = frameContext.getRenderProfile();

        int steps = profile.getRaymarchSteps();
        float maxDistance = profile.getMaxRenderDistance();
        float resolutionScale = profile.getResolutionScale();
    }
}
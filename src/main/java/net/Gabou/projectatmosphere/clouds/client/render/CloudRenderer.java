package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.client.CloudRenderController;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Point d'entrée du futur rendu live des nuages.
 * Cette classe ne gère pas le rendu debug et ne lit jamais debugSnapshot.
 */
public final class CloudRenderer {

    private CloudRenderer() {

    }

    /**
     * Prépare le rendu live des nuages à partir du contexte de frame courant.
     *
     * @param frameContext contexte de rendu de la frame courante
     */
    public static void render(@NotNull CloudRenderFrameContext frameContext) {
        CloudRenderTargetManager.prepareTargets();
        for (CloudRenderSnapshot snapshot : CloudRenderController.getRenderableLiveSnapshots()) {
            renderSnapshot(frameContext, snapshot);
        }
    }

    /**
     * Route un snapshot live valide vers la passe de rendu appropriée.
     *
     * @param frameContext contexte de rendu de la frame courante
     * @param snapshot snapshot live valide
     */
    private static void renderSnapshot(
            @NotNull CloudRenderFrameContext frameContext,
            @Nullable CloudRenderSnapshot snapshot
    ) {
        if (snapshot == null) {
            return;
        }
        CloudRaymarchRenderer.renderSnapshot(frameContext, snapshot);
    }
}

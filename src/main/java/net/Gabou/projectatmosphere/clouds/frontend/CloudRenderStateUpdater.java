package net.Gabou.projectatmosphere.clouds.frontend;

import net.Gabou.projectatmosphere.clouds.backend.CloudRegionRenderData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Met à jour l'état de rendu live des nuages.
 * Cette classe écrit seulement dans currentSnapshot et ne touche jamais au debugSnapshot.
 */
public final class CloudRenderStateUpdater {

    private CloudRenderStateUpdater() {

    }

    /**
     * Met à jour le snapshot live à partir d'une donnée de région transportable.
     *
     * @param renderData donnée transportable venant du backend
     * @param worldTime temps monde côté client
     * @param partialTick interpolation de rendu
     * @param cameraPosition position actuelle de la caméra
     */
    public static void updateCurrentSnapshot(
            @NotNull CloudRegionRenderData renderData,
            long worldTime,
            float partialTick,
            @NotNull Vec3 cameraPosition
    ) {
        CloudRenderSnapshot snapshot = CloudRenderSnapshotBuilder.create(
                renderData,
                worldTime,
                partialTick,
                cameraPosition
        );

        CloudRenderStateHolder.getInstance().setCurrentSnapshot(snapshot);
    }

    /**
     * Met à jour le snapshot live si la donnée reçue est valide.
     *
     * @param renderData donnée transportable venant du backend
     * @param worldTime temps monde côté client
     * @param partialTick interpolation de rendu
     * @param cameraPosition position actuelle de la caméra
     */
    public static void updateCurrentSnapshotIfPresent(
            @Nullable CloudRegionRenderData renderData,
            long worldTime,
            float partialTick,
            @Nullable Vec3 cameraPosition
    ) {
        if (renderData == null || cameraPosition == null) {
            clearCurrentSnapshot();
            return;
        }

        updateCurrentSnapshot(renderData, worldTime, partialTick, cameraPosition);
    }

    /**
     * Efface le snapshot live courant.
     */
    public static void clearCurrentSnapshot() {
        CloudRenderStateHolder.getInstance().clearCurrentSnapshot();
    }
}
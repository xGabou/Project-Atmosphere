package net.Gabou.projectatmosphere.clouds.client;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Met à jour l'état de rendu live des nuages.
 * Cette classe écrit seulement dans currentSnapshots et ne touche jamais au debugSnapshot.
 */
public final class CloudRenderStateUpdater {

    private CloudRenderStateUpdater() {

    }

    /**
     * Met à jour les snapshots live à partir de données de régions transportables.
     *
     * @param renderDataList données transportables venant du backend
     * @param worldTime temps monde côté client
     * @param partialTick interpolation de rendu
     * @param cameraPosition position actuelle de la caméra
     */
    public static void updateCurrentSnapshots(
            @Nullable Collection<CloudRegionRenderData> renderDataList,
            long worldTime,
            float partialTick,
            @Nullable Vec3 cameraPosition
    ) {
        if (renderDataList == null || renderDataList.isEmpty() || cameraPosition == null) {
            clearCurrentSnapshots();
            return;
        }

        List<CloudRenderSnapshot> snapshots = new ArrayList<>();

        for (CloudRegionRenderData renderData : renderDataList) {
            if (renderData == null) {
                continue;
            }

            snapshots.add(CloudRenderSnapshotBuilder.create(
                    renderData,
                    worldTime,
                    partialTick,
                    cameraPosition
            ));
        }

        if (snapshots.isEmpty()) {
            clearCurrentSnapshots();
            return;
        }

        CloudRenderStateHolder.getInstance().setCurrentSnapshots(snapshots);
    }

    /**
     * Met à jour les snapshots live avec une seule donnée de région si elle est valide.
     *
     * @param renderData donnée transportable venant du backend
     * @param worldTime temps monde côté client
     * @param partialTick interpolation de rendu
     * @param cameraPosition position actuelle de la caméra
     */
    public static void updateSingleCurrentSnapshotIfPresent(
            @Nullable CloudRegionRenderData renderData,
            long worldTime,
            float partialTick,
            @Nullable Vec3 cameraPosition
    ) {
        if (renderData == null || cameraPosition == null) {
            clearCurrentSnapshots();
            return;
        }

        updateCurrentSnapshots(
                List.of(renderData),
                worldTime,
                partialTick,
                cameraPosition
        );
    }

    /**
     * Efface les snapshots live courants.
     */
    public static void clearCurrentSnapshots() {
        CloudRenderStateHolder.getInstance().clearCurrentSnapshots();
    }
}
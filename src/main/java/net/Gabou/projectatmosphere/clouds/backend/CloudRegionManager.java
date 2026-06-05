package net.Gabou.projectatmosphere.clouds.backend;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Gère les régions de nuage backend de Project Atmosphere.
 * Cette classe centralise la création, la suppression et l'exposition des données de rendu.
 */
public final class CloudRegionManager {

    private static final CloudRegionManager INSTANCE = new CloudRegionManager();

    private final CloudRegionMotionController motionController = new CloudRegionMotionController();
    private final CloudRegionLifecycleController lifecycleController = new CloudRegionLifecycleController();

    private CloudRegionManager() {

    }

    public int getCloudRegionCount(ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).size();
    }

    /**
     * Retourne l'instance unique du manager de régions de nuage.
     *
     * @return instance unique du manager
     */
    public static @NotNull CloudRegionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Crée une nouvelle région de nuage backend et la sauvegarde dans le registre persistant.
     *
     * @param level niveau serveur
     * @param center centre du nuage en coordonnées monde
     * @param radius rayon horizontal du nuage
     * @param baseY limite verticale basse
     * @param topY limite verticale haute
     * @param density densité du nuage
     * @param coverage couverture du nuage
     * @param edgeSoftness douceur des bords du nuage
     * @param sourceRegionKey région météo source du nuage
     * @return région de nuage créée
     */
    public @NotNull CloudRegionState createCloudRegion(
            @NotNull ServerLevel level,
            @NotNull Vec3 center,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            float edgeSoftness,
            @Nullable RegionInstanceKey sourceRegionKey
    ) {
        CloudRegionState state = new CloudRegionState(
                UUID.randomUUID(),
                level.dimension(),
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                edgeSoftness,
                sourceRegionKey
        );

        CloudRegionRegistry registry = CloudRegionBackend.getRegistry(level);
        registry.add(state);
        CloudRegionBackend.markDirty(level);

        return state;
    }

    /**
     * Supprime une région de nuage backend.
     *
     * @param level niveau serveur
     * @param regionId identifiant de la région de nuage
     */
    public void removeCloudRegion(@NotNull ServerLevel level, @NotNull UUID regionId) {
        CloudRegionRegistry registry = CloudRegionBackend.getRegistry(level);
        registry.remove(regionId);
        CloudRegionBackend.markDirty(level);
    }



    /**
     * Supprime toutes les régions de nuage backend du niveau.
     *
     * @param level niveau serveur
     */
    public void clearCloudRegions(@NotNull ServerLevel level) {
        CloudRegionRegistry registry = CloudRegionBackend.getRegistry(level);
        registry.clear();
        CloudRegionBackend.markDirty(level);
    }

    /**
     * Retourne les données transportables des régions de nuage actives.
     *
     * @param level niveau serveur
     * @return données transportables des régions actives
     */
    public @NotNull Collection<CloudRegionRenderData> getActiveRenderData(@NotNull ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).createRenderDataForActiveRegions();
    }

    /**
     * Met à jour les régions de nuage backend.
     * Le mouvement, la croissance et la durée de vie seront branchés ici plus tard.
     *
     * @param level niveau serveur
     */
    public void tickCloudRegions(@NotNull ServerLevel level) {
        CloudRegionRegistry registry = CloudRegionBackend.getRegistry(level);
        boolean changed = false;

        for (CloudRegionState state : registry.getActiveRegions()) {
            if (state == null) {
                continue;
            }

            changed |= motionController.tick(level, state);
            changed |= lifecycleController.tick(level, state);
        }

        if (changed) {
            CloudRegionBackend.markDirty(level);
        }
    }
}
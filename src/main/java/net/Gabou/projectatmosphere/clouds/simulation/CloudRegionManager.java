package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Gère les régions de nuage backend de Project Atmosphere.
 * Cette classe centralise la création, la suppression et l'exposition des données de rendu.
 */
public final class CloudRegionManager {

    private static final CloudRegionManager INSTANCE = new CloudRegionManager();

    private final CloudRegionMotionController motionController = new CloudRegionMotionController();
    private final CloudRegionLifecycleController lifecycleController = new CloudRegionLifecycleController();
    private final CloudRegionEvolutionController evolutionController = new CloudRegionEvolutionController();

    private CloudRegionManager() {

    }

    public int getCloudRegionCount(ServerLevel level) {
        return CloudRegionStateStore.size(level);
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

        CloudRegionStateStore.add(level, state);

        return state;
    }

    /**
     * Crée une nouvelle région avec un type de nuage PA explicite.
     *
     * @param level niveau serveur
     * @param center centre du nuage en coordonnées monde
     * @param radius rayon horizontal initial
     * @param baseY limite verticale basse initiale
     * @param topY limite verticale haute initiale
     * @param density densité initiale
     * @param coverage couverture initiale
     * @param edgeSoftness douceur initiale des bords
     * @param sourceRegionKey région météo source
     * @param cloudTypeId identifiant de type de nuage
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
            @Nullable RegionInstanceKey sourceRegionKey,
            @NotNull String cloudTypeId
    ) {
        CloudRegionState state = createCloudRegion(
                level,
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                edgeSoftness,
                sourceRegionKey
        );

        state.setCloudTypeId(cloudTypeId);
        state.setPreviousCloudTypeId(state.getCloudTypeId());
        CloudRegionTypeGeometry.apply(state, state.getCloudTypeId());
        CloudRegionStateStore.markDirty(level);

        return state;
    }

    /**
     * Supprime une région de nuage backend.
     *
     * @param level niveau serveur
     * @param regionId identifiant de la région de nuage
     */
    public void removeCloudRegion(@NotNull ServerLevel level, @NotNull UUID regionId) {
        CloudRegionStateStore.remove(level, regionId);
    }



    /**
     * Supprime toutes les régions de nuage backend du niveau.
     *
     * @param level niveau serveur
     */
    public void clearCloudRegions(@NotNull ServerLevel level) {
        CloudRegionStateStore.clear(level);
    }

    /**
     * Supprime les régions inactives du registre backend.
     *
     * @param level niveau serveur
     * @return nombre de régions supprimées
     */
    public int clearInactiveCloudRegions(@NotNull ServerLevel level) {
        return CloudRegionStateStore.removeInactiveRegions(level);
    }

    /**
     * Retourne les données transportables des régions de nuage actives.
     *
     * @param level niveau serveur
     * @return données transportables des régions actives
     */
    public @NotNull Collection<CloudRegionRenderData> getActiveRenderData(@NotNull ServerLevel level) {
        return CloudRegionStateStore.createRenderDataForActiveRegions(level);
    }

    /**
     * Retourne des lignes de diagnostic lisibles pour les régions sauvegardées.
     *
     * @param level niveau serveur
     * @return lignes de diagnostic des régions de nuage
     */
    public @NotNull List<String> describeCloudRegions(@NotNull ServerLevel level) {
        List<String> lines = new ArrayList<>();

        for (CloudRegionState state : CloudRegionStateStore.getAll(level)) {
            if (state == null) {
                continue;
            }

            Vec3 center = state.getCenter();
            lines.add(String.format(
                    Locale.ROOT,
                    "%s active=%s type=%s typeTicks=%d center=%.1f %.1f %.1f radius=%.1f age=%d/%d density=%.2f coverage=%.2f growth=%.2f decay=%.2f",
                    state.getRegionId(),
                    state.isActive(),
                    state.getCloudTypeId(),
                    state.getCloudTypeTicks(),
                    center.x(),
                    center.y(),
                    center.z(),
                    state.getRadius(),
                    state.getAgeTicks(),
                    state.getLifetimeTicks(),
                    state.getDensity(),
                    state.getCoverage(),
                    state.getGrowth(),
                    state.getDecay()
            ));
        }

        return lines;
    }

    /**
     * Met à jour les régions de nuage backend.
     * Le mouvement, la croissance, la disparition et la durée de vie passent ici.
     *
     * @param level niveau serveur
     */
    public void tickCloudRegions(@NotNull ServerLevel level) {
        boolean changed = false;

        for (CloudRegionState state : CloudRegionStateStore.getActiveRegions(level)) {
            if (state == null) {
                continue;
            }

            changed |= motionController.tick(level, state);
            changed |= lifecycleController.tick(level, state);
            changed |= evolutionController.tick(level, state);
        }

        if (changed) {
            CloudRegionStateStore.markDirty(level);
        }
    }
}

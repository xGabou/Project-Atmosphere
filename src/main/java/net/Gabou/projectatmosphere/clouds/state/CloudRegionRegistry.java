package net.Gabou.projectatmosphere.clouds.state;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderDataFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registre interne des régions de nuage backend.
 * Cette classe est volontairement package private afin d'éviter les accès directs depuis l'extérieur du backend.
 */
final class CloudRegionRegistry {

    private final ConcurrentMap<UUID, CloudRegionState> regionsById = new ConcurrentHashMap<>();

    void add(@NotNull CloudRegionState state) {
        regionsById.put(state.getRegionId(), state);
    }

    @Nullable CloudRegionState get(@NotNull UUID regionId) {
        return regionsById.get(regionId);
    }

    void remove(@NotNull UUID regionId) {
        regionsById.remove(regionId);
    }

    boolean contains(@NotNull UUID regionId) {
        return regionsById.containsKey(regionId);
    }

    Collection<CloudRegionState> getAll() {
        return Collections.unmodifiableCollection(regionsById.values());
    }

    Collection<CloudRegionState> getActiveRegions() {
        ArrayList<CloudRegionState> activeRegions = new ArrayList<>();

        for (CloudRegionState state : regionsById.values()) {
            if (state != null && state.isActive()) {
                activeRegions.add(state);
            }
        }

        return activeRegions;
    }

    Collection<CloudRegionRenderData> createRenderDataForActiveRegions() {
        ArrayList<CloudRegionRenderData> renderDataList = new ArrayList<>();

        for (CloudRegionState state : regionsById.values()) {
            if (state == null || !state.isActive()) {
                continue;
            }

            for (CloudClusterState cluster : state.getClusters()) {
                if (cluster != null && cluster.isActive()) {
                    renderDataList.add(CloudRegionRenderDataFactory.createForCluster(state, cluster));
                }
            }
        }

        return renderDataList;
    }

    void clear() {
        regionsById.clear();
    }

    int removeInactiveRegions() {
        int removed = 0;

        for (CloudRegionState state : new ArrayList<>(regionsById.values())) {
            if (state != null && !state.isActive() && regionsById.remove(state.getRegionId(), state)) {
                removed++;
            }
        }

        return removed;
    }

    int size() {
        return regionsById.size();
    }
}

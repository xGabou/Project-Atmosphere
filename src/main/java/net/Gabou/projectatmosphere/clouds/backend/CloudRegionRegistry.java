package net.Gabou.projectatmosphere.clouds.backend;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores backend cloud region states owned by Project Atmosphere.
 * This class owns cloud region lookup only. It does not render, sync, or run weather simulation.
 */
public final class CloudRegionRegistry {

    private final ConcurrentMap<UUID, CloudRegionState> regionsById = new ConcurrentHashMap<>();

    public void add(@NotNull CloudRegionState state) {
        regionsById.put(state.getRegionId(), state);
    }

    public @Nullable CloudRegionState get(@NotNull UUID regionId) {
        return regionsById.get(regionId);
    }

    public void remove(@NotNull UUID regionId) {
        regionsById.remove(regionId);
    }

    public boolean contains(@NotNull UUID regionId) {
        return regionsById.containsKey(regionId);
    }

    public Collection<CloudRegionState> getAll() {
        return Collections.unmodifiableCollection(regionsById.values());
    }

    public Collection<CloudRegionState> getActiveRegions() {
        ArrayList<CloudRegionState> activeRegions = new ArrayList<>();

        for (CloudRegionState state : regionsById.values()) {
            if (state != null && state.isActive()) {
                activeRegions.add(state);
            }
        }

        return activeRegions;
    }

    public Collection<CloudRegionRenderData> createRenderDataForActiveRegions() {
        ArrayList<CloudRegionRenderData> renderDataList = new ArrayList<>();

        for (CloudRegionState state : regionsById.values()) {
            if (state != null && state.isActive()) {
                renderDataList.add(CloudRegionRenderDataFactory.create(state));
            }
        }

        return renderDataList;
    }

    public void clear() {
        regionsById.clear();
    }

    public int size() {
        return regionsById.size();
    }
}
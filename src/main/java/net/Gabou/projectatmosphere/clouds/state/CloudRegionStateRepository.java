package net.Gabou.projectatmosphere.clouds.state;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.UUID;

/** Persistence port for authoritative cloud-region state. */
public interface CloudRegionStateRepository {
    void add(ServerLevel level, CloudRegionState state);

    void remove(ServerLevel level, UUID regionId);

    void clear(ServerLevel level);

    int removeInactiveRegions(ServerLevel level);

    Collection<CloudRegionState> getAll(ServerLevel level);

    Collection<CloudRegionState> getActiveRegions(ServerLevel level);

    Collection<CloudRegionRenderData> createRenderDataForActiveRegions(ServerLevel level);

    int size(ServerLevel level);

    void markDirty(ServerLevel level);
}

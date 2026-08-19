package net.Gabou.projectatmosphere.clouds.state;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.UUID;

/** Minecraft 1.20.1 SavedData-backed cloud-region repository adapter. */
public final class SavedDataCloudRegionStateRepository implements CloudRegionStateRepository {
    @Override
    public void add(ServerLevel level, CloudRegionState state) {
        CloudRegionBackend.getRegistry(level).add(state);
        markDirty(level);
    }

    @Override
    public void remove(ServerLevel level, UUID regionId) {
        CloudRegionBackend.getRegistry(level).remove(regionId);
        markDirty(level);
    }

    @Override
    public void clear(ServerLevel level) {
        CloudRegionBackend.getRegistry(level).clear();
        markDirty(level);
    }

    @Override
    public int removeInactiveRegions(ServerLevel level) {
        int removed = CloudRegionBackend.getRegistry(level).removeInactiveRegions();
        if (removed > 0) {
            markDirty(level);
        }
        return removed;
    }

    @Override
    public Collection<CloudRegionState> getAll(ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).getAll();
    }

    @Override
    public Collection<CloudRegionState> getActiveRegions(ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).getActiveRegions();
    }

    @Override
    public Collection<CloudRegionRenderData> createRenderDataForActiveRegions(ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).createRenderDataForActiveRegions(level.getGameTime());
    }

    @Override
    public int size(ServerLevel level) {
        return CloudRegionBackend.getRegistry(level).size();
    }

    @Override
    public void markDirty(ServerLevel level) {
        CloudRegionBackend.markDirty(level);
    }
}

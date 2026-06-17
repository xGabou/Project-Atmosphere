package net.Gabou.projectatmosphere.clouds.client;

import java.util.List;

public final class CloudRenderStateCache {

    private volatile List<CloudRenderSnapshot> currentSnapshots = List.of();
    private volatile CloudRenderSnapshot debugSnapshot;

    public CloudRenderStateCache() {

    }

    public List<CloudRenderSnapshot> getCurrentSnapshots() {
        return currentSnapshots;
    }

    public void setCurrentSnapshots(List<CloudRenderSnapshot> currentSnapshots) {
        this.currentSnapshots = currentSnapshots != null ? List.copyOf(currentSnapshots) : List.of();
    }

    public void clearCurrentSnapshots() {
        this.currentSnapshots = List.of();
    }

    public CloudRenderSnapshot getDebugSnapshot() {
        return debugSnapshot;
    }

    public void setDebugSnapshot(CloudRenderSnapshot debugSnapshot) {
        this.debugSnapshot = debugSnapshot;
    }

    public void clearDebugSnapshot() {
        this.debugSnapshot = null;
    }

    public boolean hasCurrentSnapshots() {
        return !currentSnapshots.isEmpty();
    }

    public boolean hasDebugSnapshot() {
        return debugSnapshot != null;
    }

    public void clear() {
        this.currentSnapshots = List.of();
        this.debugSnapshot = null;
    }
}
package net.Gabou.projectatmosphere.clouds.client;

public final class CloudRenderStateCache {
    private volatile CloudRenderSnapshot debugSnapshot;

    public CloudRenderStateCache() {

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

    public boolean hasDebugSnapshot() {
        return debugSnapshot != null;
    }

    public void clear() {
        this.debugSnapshot = null;
    }
}

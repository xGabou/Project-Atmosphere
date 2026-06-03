package net.Gabou.projectatmosphere.clouds;

public final class CloudRenderStateCache {

    private volatile CloudRenderSnapshot currentSnapshot;
    private volatile CloudRenderSnapshot debugSnapshot;

    public CloudRenderSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    public void setCurrentSnapshot(CloudRenderSnapshot currentSnapshot) {
        this.currentSnapshot = currentSnapshot;
    }

    public CloudRenderSnapshot getDebugSnapshot() {
        return debugSnapshot;
    }

    public void setDebugSnapshot(CloudRenderSnapshot debugSnapshot) {
        this.debugSnapshot = debugSnapshot;
    }

    public void clear() {
        this.currentSnapshot = null;
        this.debugSnapshot = null;
    }

    public CloudRenderSnapshot getRenderableSnapshot() {
        if(this.debugSnapshot !=null) {
            return this.debugSnapshot;
        }
        return currentSnapshot;
    }

    public boolean hasRenderableSnapshot() {
        return getRenderableSnapshot() != null;
    }

    public CloudRenderStateCache() {}
}

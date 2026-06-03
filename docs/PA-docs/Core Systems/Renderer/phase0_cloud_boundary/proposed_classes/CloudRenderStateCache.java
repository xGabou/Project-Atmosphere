/**
 * Temporary proposal skeleton for the Phase 0 cloud renderer boundary.
 * TODO: move into the real source tree only after the boundary is approved.
 */
public final class CloudRenderStateCache {
    /** The current immutable snapshot visible to rendering. */
    private volatile CloudRenderSnapshot currentSnapshot;

    /** Optional debug snapshot reference used by phase 0. */
    private volatile CloudRenderSnapshot debugSnapshot;

    /**
     * Sets the current snapshot.
     *
     * @param snapshot immutable cloud snapshot
     */
    public void setCurrentSnapshot(CloudRenderSnapshot snapshot) {
        this.currentSnapshot = snapshot;
    }

    /**
     * Returns the current snapshot.
     *
     * @return the current snapshot, or null if not set
     */
    public CloudRenderSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    /**
     * Sets the debug snapshot.
     *
     * @param snapshot immutable debug snapshot
     */
    public void setDebugSnapshot(CloudRenderSnapshot snapshot) {
        this.debugSnapshot = snapshot;
    }

    /**
     * Returns the debug snapshot.
     *
     * @return the debug snapshot, or null if not set
     */
    public CloudRenderSnapshot getDebugSnapshot() {
        return debugSnapshot;
    }

    /**
     * Returns the snapshot that should currently be rendered.
     *
     * @return debug snapshot if present, otherwise current snapshot
     */
    public CloudRenderSnapshot getRenderableSnapshot() {
        return debugSnapshot != null ? debugSnapshot : currentSnapshot;
    }
}

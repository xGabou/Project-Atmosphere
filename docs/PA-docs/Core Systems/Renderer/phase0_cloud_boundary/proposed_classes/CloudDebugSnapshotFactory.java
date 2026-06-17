/**
 * Temporary proposal skeleton for the Phase 0 cloud renderer boundary.
 * TODO: move into the real source tree only after the boundary is approved.
 */
public final class CloudDebugSnapshotFactory {
    /** Prevents instantiation. */
    private CloudDebugSnapshotFactory() {
    }

    /**
     * Creates the hardcoded Phase 0 fake cloud snapshot.
     *
     * @return a simple immutable debug snapshot
     */
    public static CloudRenderSnapshot createFakeSnapshot() {
        // TODO: replace with an explicit phase 0 debug source if needed later.
        return new CloudRenderSnapshot(
            true,
            "minecraft:overworld",
            0L,
            0.0f,
            new Object(),
            new Object(),
            96.0f,
            80.0f,
            120.0f,
            0.65f,
            0.80f,
            0.25f,
            0.0f,
            0.0f,
            0x88FFFFFF
        );
    }
}

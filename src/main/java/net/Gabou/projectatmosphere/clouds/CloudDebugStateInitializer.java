package net.Gabou.projectatmosphere.clouds;

public final class CloudDebugStateInitializer {
    private CloudDebugStateInitializer() {}

    public static void initialize() {
        CloudRenderStateCache cache = CloudRenderStateHolder.getInstance();
        CloudRenderSnapshot snapshot = CloudDebugSnapshotFactory.createFakeSnapshot();
        cache.setDebugSnapshot(snapshot);
    }
}

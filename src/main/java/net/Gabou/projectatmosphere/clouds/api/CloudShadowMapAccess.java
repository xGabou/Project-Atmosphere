package net.Gabou.projectatmosphere.clouds.api;

public final class CloudShadowMapAccess {
    private static volatile CloudShadowSnapshot currentSnapshot = CloudShadowSnapshot.EMPTY;

    private CloudShadowMapAccess() {
    }

    public static CloudShadowSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    public static boolean isValid() {
        return currentSnapshot.isValid();
    }

    public static float sampleShadowAt(double worldX, double worldZ) {
        return currentSnapshot.sampleShadowAt(worldX, worldZ);
    }

    public static void publishSnapshot(CloudShadowSnapshot snapshot) {
        currentSnapshot = snapshot == null ? CloudShadowSnapshot.EMPTY : snapshot;
    }

    public static void clear() {
        currentSnapshot = CloudShadowSnapshot.EMPTY;
    }
}

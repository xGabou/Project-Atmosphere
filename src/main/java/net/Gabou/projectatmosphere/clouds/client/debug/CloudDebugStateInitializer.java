package net.Gabou.projectatmosphere.clouds.client.debug;

import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateCache;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateHolder;
import net.minecraft.world.phys.Vec3;

public final class CloudDebugStateInitializer {
    private CloudDebugStateInitializer() {
    }

    public static void initialize() {
        initialize(null);
    }

    public static void initialize(Vec3 center) {
        CloudRenderStateCache cache = CloudRenderStateHolder.getInstance();
        CloudRenderSnapshot snapshot = center != null
                ? CloudDebugSnapshotFactory.createFakeSnapshot(center)
                : CloudDebugSnapshotFactory.createFakeSnapshot();

        cache.setDebugSnapshot(snapshot);
    }

    public static void clear() {
        CloudRenderStateHolder.getInstance().clearDebugSnapshot();
    }
}
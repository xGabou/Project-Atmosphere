package net.Gabou.projectatmosphere.clouds.field.backend;

import java.util.List;

/**
 * Documents the CloudField sync boundary. The current packet sends field
 * snapshots only; cloudlets remain client/GPU-derived.
 */
public record CloudFieldSyncPlan(
        List<String> serverOwned,
        List<String> clientDerivable,
        List<String> mustSync,
        List<String> clientInterpolated,
        boolean packetImplemented
) {
    public CloudFieldSyncPlan {
        serverOwned = serverOwned == null ? List.of() : List.copyOf(serverOwned);
        clientDerivable = clientDerivable == null ? List.of() : List.copyOf(clientDerivable);
        mustSync = mustSync == null ? List.of() : List.copyOf(mustSync);
        clientInterpolated = clientInterpolated == null ? List.of() : List.copyOf(clientInterpolated);
    }

    public static CloudFieldSyncPlan plannedOnly() {
        return snapshotPacketContract();
    }

    public static CloudFieldSyncPlan snapshotPacketContract() {
        return new CloudFieldSyncPlan(
                List.of(
                        "source id",
                        "source type",
                        "dimension",
                        "center",
                        "radius",
                        "baseY/topY",
                        "density",
                        "coverage",
                        "humidity influence",
                        "wind",
                        "growth",
                        "decay",
                        "vertical development",
                        "storm potential",
                        "seed",
                        "age/lifetime hints"
                ),
                List.of(
                        "stable CloudField UUID from source id + seed",
                        "cloudlet layout from field seed + CloudletId",
                        "LOD band from camera distance",
                        "hydration state/progress",
                        "active cloudlet count from LOD and hydration"
                ),
                List.of(
                        "source identity",
                        "seed",
                        "dimension",
                        "world center",
                        "radius",
                        "vertical bounds",
                        "density/coverage",
                        "wind",
                        "growth/decay",
                        "storm/vertical development hints"
                ),
                List.of(
                        "center",
                        "radius",
                        "density",
                        "coverage",
                        "growth",
                        "decay",
                        "hydration progress"
                ),
                true
        );
    }
}

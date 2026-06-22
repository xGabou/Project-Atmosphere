package net.Gabou.projectatmosphere.clouds.field.runtime;

import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldBackendAdapter;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSource;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSourceSnapshot;
import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Collects real PA backend cloud state and converts it to CloudField sources.
 * This is a read-only bridge over existing region/cluster state.
 */
public final class CloudFieldBackendSourceCollector {
    private final CloudFieldBackendAdapter adapter;

    public CloudFieldBackendSourceCollector(CloudFieldBackendAdapter adapter) {
        this.adapter = adapter == null ? new CloudFieldBackendAdapter() : adapter;
    }

    public static CloudFieldBackendSourceCollector createDefault() {
        return new CloudFieldBackendSourceCollector(new CloudFieldBackendAdapter());
    }

    public CloudFieldSourceSnapshot collect(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Collection<CloudRegionState> activeRegions = CloudRegionStateStore.getActiveRegions(level);
        List<CloudFieldSource> sources = new ArrayList<>();

        for (CloudRegionState region : activeRegions) {
            if (region == null || !region.isActive()) {
                continue;
            }

            int activeClusterCount = 0;
            for (CloudClusterState cluster : region.getClusters()) {
                if (cluster == null || !cluster.isActive()) {
                    continue;
                }
                sources.add(adapter.fromCluster(region, cluster));
                activeClusterCount++;
            }

            if (activeClusterCount == 0) {
                sources.add(adapter.fromRegion(region));
            }
        }

        return CloudFieldSourceSnapshot.of(
                sources,
                level.getGameTime(),
                level.dimension().location().toString(),
                "pa-native-active-clusters"
        );
    }
}

package net.Gabou.projectatmosphere.api.common.cloud.region;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.ScAPICloudRegion;

import java.util.List;

/**
 * Convenience bridge that exposes read-only tornado container access on {@link ScAPICloudRegion} instances.
 * This is an adapter contract only; it must not own simulation or networking policy.
 */
public interface ScAPICloudRegionTornadoAccess extends ScAPICloudRegion, ITornadoRegion {

    static ScAPICloudRegionTornadoAccess of(ScAPICloudRegion region) {
        if (region instanceof ScAPICloudRegionTornadoAccess access) {
            return access;
        }
        throw new IllegalStateException("Cloud region does not expose tornado metadata. Ensure Project Atmosphere is installed.");
    }

    default List<TornadoDescriptor> getTornadoDescriptors() {
        return getTornadoesView();
    }
}

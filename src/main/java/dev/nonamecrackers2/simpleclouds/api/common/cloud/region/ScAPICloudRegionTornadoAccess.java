package dev.nonamecrackers2.simpleclouds.api.common.cloud.region;

import java.util.List;

/**
 * Convenience bridge that exposes the tornado container methods directly on {@link ScAPICloudRegion} instances.
 * Mods should call {@link #of(ScAPICloudRegion)} to obtain an accessor and then append/remove {@link TornadoDescriptor}
 * objects as needed.  The server will automatically stream the descriptors to connected clients.
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

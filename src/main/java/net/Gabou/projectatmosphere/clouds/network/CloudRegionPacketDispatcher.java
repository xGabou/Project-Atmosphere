package net.Gabou.projectatmosphere.clouds.network;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;

import java.util.Collection;
import java.util.List;

/**
 * Common-safe indirection for client cloud-region packet handling.
 * The client side registers the sink from ClientOnlyRegistrar.
 */
public final class CloudRegionPacketDispatcher {
    private static volatile CloudRegionSink clientSink = regions -> {
    };
    private static volatile CloudRegionSupplier clientSupplier = List::of;

    private CloudRegionPacketDispatcher() {
    }

    public static void setClientSink(CloudRegionSink sink) {
        clientSink = sink == null ? regions -> {
        } : sink;
    }

    public static void setClientSupplier(CloudRegionSupplier supplier) {
        clientSupplier = supplier == null ? List::of : supplier;
    }

    public static void handleClientRegions(Collection<CloudRegionRenderData> regions) {
        clientSink.accept(regions == null ? List.of() : List.copyOf(regions));
    }

    public static Collection<CloudRegionRenderData> getClientRegions() {
        Collection<CloudRegionRenderData> regions = clientSupplier.get();
        return regions == null ? List.of() : List.copyOf(regions);
    }

    @FunctionalInterface
    public interface CloudRegionSink {
        void accept(Collection<CloudRegionRenderData> regions);
    }

    @FunctionalInterface
    public interface CloudRegionSupplier {
        Collection<CloudRegionRenderData> get();
    }
}

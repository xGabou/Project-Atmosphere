package net.Gabou.projectatmosphere.clouds.field.network;

import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;

import java.util.Collection;
import java.util.List;

/**
 * Common-safe indirection for client CloudField packet handling.
 */
public final class CloudFieldPacketDispatcher {
    private static volatile CloudFieldSink clientSink = snapshots -> {
    };
    private static volatile CloudFieldSupplier clientSupplier = List::of;
    private static volatile CloudFieldDeltaSink clientDeltaSink = (updated, removed) -> {
    };

    private CloudFieldPacketDispatcher() {
    }

    public static void setClientSink(CloudFieldSink sink) {
        clientSink = sink == null ? snapshots -> {
        } : sink;
    }

    public static void setClientSupplier(CloudFieldSupplier supplier) {
        clientSupplier = supplier == null ? List::of : supplier;
    }

    public static void setClientDeltaSink(CloudFieldDeltaSink sink) {
        clientDeltaSink = sink == null ? (updated, removed) -> {
        } : sink;
    }

    public static void handleClientSnapshots(Collection<CloudFieldSnapshot> snapshots) {
        clientSink.accept(snapshots == null ? List.of() : List.copyOf(snapshots));
    }

    public static Collection<CloudFieldSnapshot> getClientSnapshots() {
        Collection<CloudFieldSnapshot> snapshots = clientSupplier.get();
        return snapshots == null ? List.of() : List.copyOf(snapshots);
    }

    public static void handleClientDelta(
            Collection<CloudFieldSnapshot> updated,
            Collection<java.util.UUID> removed
    ) {
        clientDeltaSink.accept(
                updated == null ? List.of() : List.copyOf(updated),
                removed == null ? List.of() : List.copyOf(removed)
        );
    }

    @FunctionalInterface
    public interface CloudFieldSink {
        void accept(Collection<CloudFieldSnapshot> snapshots);
    }

    @FunctionalInterface
    public interface CloudFieldSupplier {
        Collection<CloudFieldSnapshot> get();
    }

    @FunctionalInterface
    public interface CloudFieldDeltaSink {
        void accept(Collection<CloudFieldSnapshot> updated, Collection<java.util.UUID> removed);
    }
}

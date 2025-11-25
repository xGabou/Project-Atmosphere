package net.Gabou.projectatmosphere.api.common.cloud.region;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Capability-like contract that marks a {@link dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion} as being able
 * to carry one or more {@link TornadoDescriptor}s.  Server-side controllers should construct descriptors with offsets that are
 * relative to the cloud region centre (in block coordinates), plus optional velocities for slow drift.  The associated radius,
 * bottom, and height describe the cylinder sampled in the shaders.  The list is serialized through every
 * {@link dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion} constructor, packet, and tag so clients automatically
 * receive the tornado metadata without implementing their own sync logic.
 */
public interface ITornadoRegion {
    String TORNADO_LIST_KEY = "projectatmosphere_tornadoes";

    List<TornadoDescriptor> getTornadoes();

    void setTornadoes(List<TornadoDescriptor> descriptors);

    default void clearTornadoes() {
        getTornadoes().clear();
    }

    default List<TornadoDescriptor> getTornadoesView() {
        return Collections.unmodifiableList(getTornadoes());
    }

    default void addTornado(TornadoDescriptor descriptor) {
        getTornadoes().add(descriptor);
    }

    default boolean removeTornado(UUID id) {
        return getTornadoes().removeIf(descriptor -> descriptor.getId().equals(id));
    }

    @Nullable
    default TornadoDescriptor findTornado(UUID id) {
        for (TornadoDescriptor descriptor : getTornadoes()) {
            if (descriptor.getId().equals(id)) {
                return descriptor;
            }
        }
        return null;
    }

    default void replaceTornado(TornadoDescriptor descriptor) {
        List<TornadoDescriptor> list = getTornadoes();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(descriptor.getId())) {
                list.set(i, descriptor);
                return;
            }
        }
        list.add(descriptor);
    }

    static List<TornadoDescriptor> copy(List<TornadoDescriptor> source) {
        return new ArrayList<>(source);
    }
}

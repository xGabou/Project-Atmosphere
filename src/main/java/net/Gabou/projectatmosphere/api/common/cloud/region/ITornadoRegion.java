package net.Gabou.projectatmosphere.api.common.cloud.region;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Capability-like contract for a cloud region that can carry tornado descriptors.
 * This is a container contract only; it must not own rendering or synchronization policy.
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

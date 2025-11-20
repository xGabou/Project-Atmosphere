package net.Gabou.projectatmosphere.api.common.event;

import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;

import java.util.List;

/**
 * Optional extension for {@link CloudRegionTickEvent} consumers that exposes the tornado descriptors bundled with the
 * region.  Casting an event to this interface is safe when Project Atmosphere is present on the logical side handling
 * the event.
 */
public interface CloudRegionTickEventTornadoAccess {
    List<TornadoDescriptor> getTornadoes();
}

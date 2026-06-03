package net.Gabou.projectatmosphere.api.common.event;

import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;

import java.util.List;

/**
 * Optional extension for cloud region tick events that exposes read-only tornado descriptors.
 * Consumers should treat this as a query-only view and must not mutate tornado state through it.
 */
public interface CloudRegionTickEventTornadoAccess {
    List<TornadoDescriptor> getTornadoes();
}

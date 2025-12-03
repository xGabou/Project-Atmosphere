package net.Gabou.projectatmosphere.modules.temperature.spike;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;

public record SpikeData(RegionInstanceKey regionId, float[][] week, SpikeState state) {
}

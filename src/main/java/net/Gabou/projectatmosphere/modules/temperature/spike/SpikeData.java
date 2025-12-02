package net.Gabou.projectatmosphere.modules.temperature.spike;

import net.Gabou.projectatmosphere.modules.region.ForecastRegionId;

public record SpikeData(ForecastRegionId regionId, float[][] week, SpikeState state) {
}

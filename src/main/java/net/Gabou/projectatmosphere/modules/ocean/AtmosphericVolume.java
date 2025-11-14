package net.Gabou.projectatmosphere.modules.ocean;

import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;

/**
 * Lightweight wrapper representing a single forecast cell influenced by a basin.
 */
public record AtmosphericVolume(OceanBasin basin, RegionAtmosphereState state, float weight, boolean oceanCell) {
}

package net.Gabou.projectatmosphere.modules.region;

import java.util.List;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

/**
 * Generation hook to create biome-derived forecast slices for a region.
 */
public interface BiomeForecastGenerator {
    BiomeForecastSnapshot generateSlice(List<BiomeInstanceKey> biomes, int sliceIndex);

    float factorForSlice(List<BiomeInstanceKey> biomes, int sliceIndex);
}

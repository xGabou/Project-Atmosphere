package net.Gabou.projectatmosphere.modules.region;

import java.util.Optional;
import java.util.List;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;

/**
 * Persistence boundary for region saves and biome fallback JSON.
 */
public interface RegionPersistence {
    Optional<BiomeFallbackSnapshot> loadFallback(RegionInstanceKey id);

    BiomeFallbackSnapshot saveFallback(RegionInstanceKey id, ForecastRegion.Section[] sections, List<BiomeInstanceKey> sourceBiomes);
}

package net.Gabou.projectatmosphere.modules.region;

import java.util.Optional;
import java.util.List;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

/**
 * Persistence boundary for region saves and biome fallback JSON.
 */
public interface RegionPersistence {
    Optional<BiomeFallbackSnapshot> loadFallback(ForecastRegionId id);

    BiomeFallbackSnapshot saveFallback(ForecastRegionId id, ForecastRegion.Section[] sections, List<BiomeInstanceKey> sourceBiomes);
}

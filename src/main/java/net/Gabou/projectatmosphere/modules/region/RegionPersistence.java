package net.Gabou.projectatmosphere.modules.region;

import java.util.List;
import java.util.Optional;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;

/**
 * Persistence boundary for region saves and biome fallback JSON.
 */
public interface RegionPersistence {
    boolean hasRegionData();

    List<RegionInstanceKey> listRegionIds();

    Optional<ForecastRegion> loadRegion(RegionInstanceKey id);

    void saveRegion(ForecastRegion region);

    Optional<BiomeFallbackSnapshot> loadFallback(RegionInstanceKey id);

    BiomeFallbackSnapshot saveFallback(RegionInstanceKey id, ForecastRegion.Section[] sections, List<BiomeInstanceKey> sourceBiomes);
}

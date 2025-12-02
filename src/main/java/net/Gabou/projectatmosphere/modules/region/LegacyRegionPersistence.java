package net.Gabou.projectatmosphere.modules.region;

import java.util.List;
import java.util.Optional;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

/**
 * No-op persistence that forces generation and writes an in-memory fallback snapshot.
 * Replace with integration to ForecastDataStorage for full durability.
 */
public final class LegacyRegionPersistence implements RegionPersistence {
    @Override
    public Optional<BiomeFallbackSnapshot> loadFallback(ForecastRegionId id) {
        return Optional.empty();
    }

    @Override
    public BiomeFallbackSnapshot saveFallback(ForecastRegionId id, ForecastRegion.Section[] sections, List<BiomeInstanceKey> sourceBiomes) {
        return new BiomeFallbackSnapshot(id, sourceBiomes, sections);
    }
}

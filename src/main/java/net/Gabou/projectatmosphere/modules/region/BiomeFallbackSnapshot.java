package net.Gabou.projectatmosphere.modules.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;

/**
 * Serializable container for per-section biome snapshots used as fallback when region data is missing.
 */
public final class BiomeFallbackSnapshot {
    private final RegionInstanceKey id;
    private final List<BiomeInstanceKey> sourceBiomes;
    private final ForecastRegion.Section[] sections;

    public BiomeFallbackSnapshot(RegionInstanceKey id,
                                 List<BiomeInstanceKey> sourceBiomes,
                                 ForecastRegion.Section[] sections) {
        this.id = id;
        this.sourceBiomes = Collections.unmodifiableList(new ArrayList<>(sourceBiomes));
        this.sections = sections.clone();
    }

    public RegionInstanceKey id() {
        return id;
    }

    public List<BiomeInstanceKey> sourceBiomes() {
        return sourceBiomes;
    }

    public ForecastRegion.Section[] toSections() {
        ForecastRegion.Section[] copy = new ForecastRegion.Section[sections.length];
        System.arraycopy(sections, 0, copy, 0, sections.length);
        return copy;
    }
}

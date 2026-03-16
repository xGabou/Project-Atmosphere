package net.Gabou.projectatmosphere.modules.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * RegionIndex backed by the existing RegionInstanceKey grid (2000 block cells) and a cached region->biomes mapping.
 */
public final class GridRegionIndex implements RegionIndex {
    private final int regionSize;
    private final Map<RegionInstanceKey, List<BiomeInstanceKey>> cache = new ConcurrentHashMap<>();

    public GridRegionIndex() {
        this(RegionInstanceKey.DEFAULT_REGION_SIZE);
    }

    public GridRegionIndex(int regionSize) {
        this.regionSize = regionSize;
    }

    @Override
    public RegionInstanceKey regionFor(BlockPos pos, ResourceKey<Level> dimension) {
        return RegionInstanceKey.from(pos, regionSize);
    }

    @Override
    public List<BiomeInstanceKey> biomesFor(RegionInstanceKey id) {
        return cache.computeIfAbsent(id, this::buildBiomesFor);
    }

    private List<BiomeInstanceKey> buildBiomesFor(RegionInstanceKey legacy) {
        List<BiomeInstanceKey> result = new ArrayList<>();
        for (BiomeInstanceKey key : ForecastGenerator.getBiomeSamples()) {
            if (legacy.contains(key.samplePos())) {
                result.add(key);
            }
        }
        return Collections.unmodifiableList(result);
    }
}

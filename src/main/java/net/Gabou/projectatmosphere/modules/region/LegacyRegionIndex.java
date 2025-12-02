package net.Gabou.projectatmosphere.modules.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * RegionIndex implementation that reuses the legacy RegionInstanceKey grid and existing biome samples.
 */
public final class LegacyRegionIndex implements RegionIndex {

    @Override
    public ForecastRegionId regionFor(BlockPos pos, ResourceKey<Level> dimension) {
        RegionInstanceKey legacy = RegionInstanceKey.from(pos);
        return new ForecastRegionId(legacy.regionX(), legacy.regionZ(), dimension);
    }

    @Override
    public List<BiomeInstanceKey> biomesFor(ForecastRegionId id) {
        RegionInstanceKey legacy = new RegionInstanceKey(id.rx(), id.rz(), RegionInstanceKey.DEFAULT_REGION_SIZE);
        List<BiomeInstanceKey> result = new ArrayList<>();
        for (BiomeInstanceKey key : ForecastGenerator.getBiomeSamples()) {
            if (legacy.contains(key.samplePos())) {
                result.add(key);
            }
        }
        return Collections.unmodifiableList(result);
    }
}

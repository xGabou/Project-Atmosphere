package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;

import java.util.Map;

final class AtmosphericStateLookup {
    private AtmosphericStateLookup() {
    }

    static void indexLegacyKeys(ForecastRegion region, Map<BiomeInstanceKey, RegionInstanceKey> legacyIndex) {
        RegionInstanceKey regionId = region.getKey();
        for (BiomeInstanceKey sample : region.getSamples()) {
            if (sample != null) {
                legacyIndex.put(sample, regionId);
            }
        }
    }

    static boolean isWithinRegionRadius(RegionInstanceKey key, BlockPos pos, int radiusSquared) {
        int size = key.regionSize();
        int minX = key.regionX() * size;
        int minZ = key.regionZ() * size;
        int maxX = minX + size - 1;
        int maxZ = minZ + size - 1;
        int px = pos.getX();
        int pz = pos.getZ();
        int dx = 0;
        int dz = 0;
        if (px < minX) {
            dx = minX - px;
        } else if (px > maxX) {
            dx = px - maxX;
        }
        if (pz < minZ) {
            dz = minZ - pz;
        } else if (pz > maxZ) {
            dz = pz - maxZ;
        }
        return (dx * dx + dz * dz) <= radiusSquared;
    }

    static RegionInstanceKey resolveRegionKey(BiomeInstanceKey biomeKey, Map<BiomeInstanceKey, RegionInstanceKey> legacyIndex) {
        if (biomeKey == null) {
            return null;
        }
        RegionInstanceKey mapped = legacyIndex.get(biomeKey);
        if (mapped != null) {
            return mapped;
        }
        if (biomeKey.samplePos() == null) {
            return null;
        }
        return RegionInstanceKey.from(biomeKey.samplePos());
    }
}

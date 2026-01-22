package net.Gabou.projectatmosphere.modules.region;

import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

/**
 * Convenience factory to wire the new region orchestrator to legacy data until full migration lands.
 */
public final class RegionOrchestratorBootstrap {
    private RegionOrchestratorBootstrap() {
    }

    public static RegionForecastOrchestrator bootstrap(ServerLevel level) {
        RegionIndex index = new GridRegionIndex();
        RegionPersistence persistence = new FileRegionPersistence(level);
        BiomeForecastGenerator generator = new BiomeForecastGenerator() {
            @Override
            public BiomeForecastSnapshot generateSlice(java.util.List<BiomeInstanceKey> biomes, int sliceIndex) {
                if (biomes == null || biomes.isEmpty()) {
                    return null;
                }
                BiomeInstanceKey key = biomes.get(sliceIndex % biomes.size());
                BiomeForecast forecast = ForecastGenerator.getForecastMap().get(key);
                if (forecast == null) {
                    return null;
                }
                if (forecast.getBiomeKey() == null) {
                    forecast.setBiomeKey(key);
                }
                return BiomeForecastSnapshot.from(forecast);
            }

            @Override
            public float factorForSlice(java.util.List<BiomeInstanceKey> biomes, int sliceIndex) {
                return 1f / 8f;
            }
        };
        return new RegionForecastOrchestrator(index, persistence, generator);
    }
}

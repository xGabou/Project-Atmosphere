package net.Gabou.projectatmosphere.modules.region;

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
        return new RegionForecastOrchestrator(level, index, persistence);
    }
}

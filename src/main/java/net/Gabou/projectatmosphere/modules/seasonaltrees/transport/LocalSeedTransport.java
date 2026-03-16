package net.Gabou.projectatmosphere.modules.seasonaltrees.transport;

import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeedPayload;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeasonalTreesCore;
import net.minecraft.server.level.ServerLevel;

public class LocalSeedTransport implements SeasonalTreesSeedTransport {
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean offerSeed(ServerLevel level, SeedPayload payload) {
        return SeasonalTreesCore.tryPlantSeed(level, payload);
    }

    @Override
    public void tick(ServerLevel level) {
        // No-op for local dispersal.
    }

    @Override
    public int getActiveSeedCount() {
        return 0;
    }
}

package net.Gabou.projectatmosphere.modules.seasonaltrees.transport;

import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeedPayload;
import net.minecraft.server.level.ServerLevel;

public interface SeasonalTreesSeedTransport {
    boolean isEnabled();

    boolean offerSeed(ServerLevel level, SeedPayload payload);

    void tick(ServerLevel level);

    int getActiveSeedCount();
}

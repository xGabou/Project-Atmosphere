package net.Gabou.projectatmosphere.gameplay;

import net.Gabou.projectatmosphere.modules.wind.WindForces;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class WindPhysics {
    private WindPhysics() { }

    public static void onServerTick(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            WindForces.applyToPlayer(level, p, 1.0f);
        }
    }
}


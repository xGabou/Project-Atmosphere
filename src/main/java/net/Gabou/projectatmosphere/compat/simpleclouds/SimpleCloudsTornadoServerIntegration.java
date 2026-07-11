package net.Gabou.projectatmosphere.compat.simpleclouds;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Loaded only through the dependency-safe server facade when Simple Clouds exists. */
public final class SimpleCloudsTornadoServerIntegration {
    private SimpleCloudsTornadoServerIntegration() {
    }

    public static boolean spawn(ServerLevel level, Vec3 position, float radius, WindVector wind, int stormLevel) {
        return TornadoManager.spawnServer(level, position, radius, wind, stormLevel);
    }
}

package net.Gabou.projectatmosphere.compat.simpleclouds;

import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/** Optional Simple Clouds tornado facade with no external types in its ABI. */
public final class SimpleCloudsTornadoServerHooks {
    private static volatile Method spawnMethod;

    private SimpleCloudsTornadoServerHooks() {
    }

    public static boolean spawn(ServerLevel level, Vec3 position, float radius, WindVector wind, int stormLevel) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            throw new IllegalStateException("Simple Clouds tornado spawn invoked without Simple Clouds");
        }
        try {
            Method method = spawnMethod;
            if (method == null) {
                Class<?> type = Class.forName(
                        "net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsTornadoServerIntegration",
                        true,
                        SimpleCloudsTornadoServerHooks.class.getClassLoader()
                );
                method = type.getMethod("spawn", ServerLevel.class, Vec3.class, float.class, WindVector.class, int.class);
                spawnMethod = method;
            }
            return (Boolean) method.invoke(null, level, position, radius, wind, stormLevel);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Simple Clouds is present but PA's tornado server integration could not load", exception);
        }
    }
}

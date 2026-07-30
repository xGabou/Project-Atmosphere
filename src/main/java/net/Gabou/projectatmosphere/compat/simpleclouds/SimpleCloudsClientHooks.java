package net.Gabou.projectatmosphere.compat.simpleclouds;

import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.minecraft.client.multiplayer.ClientLevel;

import java.lang.reflect.Method;

/**
 * Dependency-safe facade for client-only Simple Clouds tornado work. No
 * optional type occurs in this class's fields or method descriptors.
 */
public final class SimpleCloudsClientHooks {
    private static volatile Methods methods;

    private SimpleCloudsClientHooks() {
    }

    public static void tickTornadoes(ClientLevel level, int clientTick) {
        invoke().tick(level, clientTick);
    }

    public static void clearTornadoes() {
        invoke().clear();
    }

    public static void releaseRenderResources() {
        invoke().releaseRenderResources();
    }

    public static void logCloudDiagnostic(double x, double z, ClientLevel level) {
        invoke().logDiagnostic(x, z, level);
    }

    private static Methods invoke() {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            throw new IllegalStateException("Simple Clouds client hooks were invoked while Simple Clouds is absent");
        }
        Methods cached = methods;
        if (cached != null) {
            return cached;
        }
        synchronized (SimpleCloudsClientHooks.class) {
            cached = methods;
            if (cached != null) {
                return cached;
            }
            try {
                Class<?> type = Class.forName(
                        "net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsTornadoClientIntegration",
                        true,
                        SimpleCloudsClientHooks.class.getClassLoader()
                );
                cached = new Methods(
                        type.getMethod("tick", ClientLevel.class, int.class),
                        type.getMethod("clear"),
                        type.getMethod("releaseRenderResources"),
                        type.getMethod("logDiagnostic", double.class, double.class, ClientLevel.class)
                );
                methods = cached;
                return cached;
            } catch (ReflectiveOperationException | LinkageError exception) {
                throw new IllegalStateException("Simple Clouds is present but PA's tornado client integration could not load", exception);
            }
        }
    }

    private record Methods(Method tickMethod, Method clearMethod, Method releaseMethod, Method diagnosticMethod) {
        private void tick(ClientLevel level, int clientTick) {
            call(tickMethod, level, clientTick);
        }

        private void clear() {
            call(clearMethod);
        }

        private void releaseRenderResources() {
            call(releaseMethod);
        }

        private void logDiagnostic(double x, double z, ClientLevel level) {
            call(diagnosticMethod, x, z, level);
        }

        private static void call(Method method, Object... args) {
            try {
                method.invoke(null, args);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Simple Clouds tornado client hook invocation failed", exception);
            }
        }
    }
}

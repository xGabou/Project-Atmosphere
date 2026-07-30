package net.Gabou.projectatmosphere.compat.simpleclouds;

import net.Gabou.projectatmosphere.client.render.TornadoClientEffects;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsHurricaneRenderer;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsTornadoRenderer;
import net.Gabou.projectatmosphere.client.sound.TornadoAudioClient;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.HashSet;
import java.util.Set;

/**
 * Simple-Clouds-only tornado client work. This class is resolved exclusively
 * through {@link SimpleCloudsClientHooks} after the optional mod is present.
 */
public final class SimpleCloudsTornadoClientIntegration {
    private static final Set<TornadoInstance> PREVIOUS = new HashSet<>();

    private SimpleCloudsTornadoClientIntegration() {
    }

    public static void tick(ClientLevel level, int clientTick) {
        if (level == null) {
            clear();
            return;
        }
        TornadoManager.tick(level);
        Set<TornadoInstance> current = new HashSet<>(TornadoManager.getClientTornadoes());
        for (TornadoInstance tornado : current) {
            TornadoAudioClient.ensure(tornado, 0.85F, 384.0F);
            TornadoClientEffects.tickTornadoDust(tornado, level, clientTick);
        }
        for (TornadoInstance tornado : PREVIOUS) {
            if (!current.contains(tornado)) {
                TornadoAudioClient.stop(tornado);
            }
        }
        PREVIOUS.clear();
        PREVIOUS.addAll(current);
    }

    public static void clear() {
        TornadoManager.clearClientTornadoes();
        TornadoAudioClient.stopAll();
        PREVIOUS.clear();
    }

    public static void releaseRenderResources() {
        SimpleCloudsTornadoRenderer.INSTANCE.close();
        SimpleCloudsHurricaneRenderer.INSTANCE.close();
    }

    public static void logDiagnostic(double x, double z, ClientLevel level) {
        SimpleCloudsCompat.logDiagnostic(x, z, level);
    }
}

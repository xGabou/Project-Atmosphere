package net.Gabou.projectatmosphere.client;

import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.particles.DebrisParticleData;
import net.minecraft.client.multiplayer.ClientLevel;

public final class TornadoClientEffects {
    private TornadoClientEffects() {
    }

    public static void spawnDebrisParticles(TornadoInstance tornado, ClientLevel level) {
        for (int i = 0; i < 10; i++) {
            double maxRadius = Math.max(4.0, tornado.radius);
            double radius = Math.sqrt(level.random.nextDouble()) * maxRadius;
            double height = level.random.nextDouble() * Math.min(
                    tornado.getVisualHeight(),
                    SimpleCloudsConfig.CLIENT.cloudHeight.get()
            );
            float angularSpeed = 4.0F;

            level.addParticle(new DebrisParticleData(tornado, radius, height, angularSpeed),
                    tornado.position.x, tornado.position.y, tornado.position.z, 0.0, 0.01, 0.0);
        }
    }
}

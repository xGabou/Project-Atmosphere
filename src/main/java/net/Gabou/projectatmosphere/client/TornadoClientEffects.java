package net.Gabou.projectatmosphere.client;

import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.particles.DebrisParticleData;
import net.minecraft.client.multiplayer.ClientLevel;

public final class TornadoClientEffects {
    private TornadoClientEffects() {
    }

    public static void spawnDebrisParticles(TornadoInstance tornado, ClientLevel level) {
        double visualHeight = Math.min(tornado.getRenderHeight(1.0F), SimpleCloudsConfig.CLIENT.cloudHeight.get());
        double maxRadius = Math.max(4.0, tornado.getRenderRadius(1.0F));
        float intensity = tornado.getNormalizedIntensity();
        float debrisScore = tornado.getRecentDebrisScore();

        int lowCount = 7 + Math.round(intensity * 7.0F + debrisScore * 10.0F);
        int midCount = 12 + Math.round(intensity * 8.0F + debrisScore * 7.0F);
        int upperCount = 4 + Math.round(intensity * 4.0F + debrisScore * 2.0F);

        spawnBand(level, tornado, lowCount, maxRadius * 1.24D, visualHeight * 0.20D, 8.4F);
        spawnBand(level, tornado, midCount, maxRadius * 0.76D, visualHeight * 0.74D, 16.0F);
        spawnBand(level, tornado, upperCount, maxRadius * 1.24D, visualHeight * 1.06D, 5.4F);
    }

    private static void spawnBand(ClientLevel level, TornadoInstance tornado, int count, double maxRadius, double maxHeight,
                                  float angularSpeed) {
        for (int i = 0; i < count; i++) {
            double radius = Math.sqrt(level.random.nextDouble()) * Math.max(0.6D, maxRadius);
            double height = level.random.nextDouble() * Math.max(1.0D, maxHeight);
            float localAngularSpeed = (float) (angularSpeed * (0.82F + level.random.nextFloat() * 0.42F));

            level.addParticle(
                    new DebrisParticleData(tornado, radius, height, localAngularSpeed),
                    tornado.getRenderPosition(1.0F).x,
                    tornado.getRenderBottomY(1.0F),
                    tornado.getRenderPosition(1.0F).z,
                    0.0,
                    0.0,
                    0.0
            );
        }
    }
}

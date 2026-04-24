package net.Gabou.projectatmosphere.client;

import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.particles.DebrisParticleData;
import net.minecraft.client.multiplayer.ClientLevel;

public final class TornadoClientEffects {
    private static final int LOW_BAND = 0;
    private static final int MID_BAND = 1;
    private static final int UPPER_BAND = 2;

    private TornadoClientEffects() {
    }

    public static void spawnDebrisParticles(TornadoInstance tornado, ClientLevel level) {
        double visualHeight = Math.min(tornado.getVisualHeight(), SimpleCloudsConfig.CLIENT.cloudHeight.get());
        double maxRadius = Math.max(4.0, tornado.radius);
        float intensity = tornado.getNormalizedIntensity();
        float debrisScore = tornado.getRecentDebrisScore();

        int lowCount = 7 + Math.round(intensity * 7.0F + debrisScore * 10.0F);
        int midCount = 12 + Math.round(intensity * 8.0F + debrisScore * 7.0F);
        int upperCount = 4 + Math.round(intensity * 4.0F + debrisScore * 2.0F);

        spawnBand(level, tornado, lowCount, maxRadius * 1.24D, visualHeight * 0.20D, 8.4F, 0.020F, 0.42F, LOW_BAND);
        spawnBand(level, tornado, midCount, maxRadius * 0.76D, visualHeight * 0.74D, 16.0F, 0.046F, 0.30F, MID_BAND);
        spawnBand(level, tornado, upperCount, maxRadius * 1.24D, visualHeight * 1.06D, 5.4F, 0.026F, 0.46F, UPPER_BAND);
    }

    private static void spawnBand(ClientLevel level, TornadoInstance tornado, int count, double maxRadius, double maxHeight,
                                  float angularSpeed, float verticalDrift, float radialJitter, int band) {
        for (int i = 0; i < count; i++) {
            double radius = Math.sqrt(level.random.nextDouble()) * Math.max(0.6D, maxRadius);
            double height = level.random.nextDouble() * Math.max(1.0D, maxHeight);
            float localAngularSpeed = (float) (angularSpeed * (0.82F + level.random.nextFloat() * 0.42F));
            float localVerticalDrift = verticalDrift * (0.75F + level.random.nextFloat() * 0.55F);
            float localRadialJitter = radialJitter * (0.65F + level.random.nextFloat() * 0.70F);

            level.addParticle(
                    new DebrisParticleData(tornado, radius, height, localAngularSpeed, localVerticalDrift, localRadialJitter, band),
                    tornado.position.x,
                    tornado.position.y,
                    tornado.position.z,
                    0.0,
                    localVerticalDrift,
                    0.0
            );
        }
    }
}

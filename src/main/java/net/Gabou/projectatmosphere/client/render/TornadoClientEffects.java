package net.Gabou.projectatmosphere.client.render;

import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.particles.DebrisParticleData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class TornadoClientEffects {
    private static final int LOW_BAND = 0;
    private static final int MID_BAND = 1;
    private static final int UPPER_BAND = 2;
    private static final int DUST_SPAWN_INTERVAL_TICKS = 4;
    private static final int DUST_LIFETIME_ESTIMATE_TICKS = 96;

    private TornadoClientEffects() {
    }

    public static void tickTornadoDust(TornadoInstance tornado, ClientLevel level, int clientTick) {
        if (clientTick % DUST_SPAWN_INTERVAL_TICKS != 0 || tornado.getNormalizedIntensity() <= 0.04F) {
            return;
        }
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 tornadoPos = tornado.getRenderPosition(1.0F);
        double visualRadius = Math.max(12.0D, tornado.getRenderRadius(1.0F));
        int targetActiveParticles = Mth.clamp(
                Mth.floor(30.0F + tornado.getNormalizedIntensity() * 30.0F + (float)Math.min(visualRadius, 500.0D) / 500.0F * 18.0F),
                18,
                60
        );
        int count = Math.max(1, Mth.ceil((float)targetActiveParticles * DUST_SPAWN_INTERVAL_TICKS / DUST_LIFETIME_ESTIMATE_TICKS));
        spawnFallingDustCurtain(level, tornado, cameraPos, tornadoPos, visualRadius, count);
    }

    public static void spawnDebrisParticles(TornadoInstance tornado, ClientLevel level) {
        double visualHeight = Math.min(tornado.getRenderHeight(1.0F), SimpleCloudsConfig.CLIENT.cloudHeight.get());
        double maxRadius = Math.max(4.0, tornado.getRenderRadius(1.0F));
        float intensity = tornado.getNormalizedIntensity();
        float debrisScore = tornado.getRecentDebrisScore();

        int lowCount = 7 + Math.round(intensity * 7.0F + debrisScore * 10.0F);
        int midCount = 12 + Math.round(intensity * 8.0F + debrisScore * 7.0F);
        int upperCount = 4 + Math.round(intensity * 4.0F + debrisScore * 2.0F);

        spawnBand(level, tornado, lowCount, maxRadius * 1.24D, visualHeight * 0.20D, 8.4F, 0.020F, 0.42F, LOW_BAND);
        spawnBand(level, tornado, midCount, maxRadius * 0.76D, visualHeight * 0.74D, 16.0F, 0.046F, 0.30F, MID_BAND);
        spawnBand(level, tornado, upperCount, maxRadius * 1.24D, visualHeight * 1.06D, 5.4F, 0.026F, 0.46F, UPPER_BAND);
    }

    private static void spawnFallingDustCurtain(ClientLevel level, TornadoInstance tornado, Vec3 cameraPos,
                                                Vec3 tornadoPos, double visualRadius, int count) {
        Vec3 cameraToTornado = new Vec3(tornadoPos.x - cameraPos.x, 0.0D, tornadoPos.z - cameraPos.z);
        if (cameraToTornado.lengthSqr() < 1.0E-4D) {
            cameraToTornado = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 behindDir = cameraToTornado.normalize();
        Vec3 lateralDir = new Vec3(-behindDir.z, 0.0D, behindDir.x);
        double curtainRadius = Mth.clamp(visualRadius * 2.2D, 28.0D, 500.0D);
        double nearDistance = visualRadius * 0.80D;
        double farDistance = curtainRadius;

        for (int i = 0; i < count; i++) {
            double distanceBehind = nearDistance + level.random.nextDouble() * Math.max(4.0D, farDistance - nearDistance);
            double lateral = (level.random.nextDouble() * 2.0D - 1.0D) * curtainRadius * 0.52D;
            double spawnX = tornadoPos.x + behindDir.x * distanceBehind + lateralDir.x * lateral;
            double spawnZ = tornadoPos.z + behindDir.z * distanceBehind + lateralDir.z * lateral;
            BlockPos column = BlockPos.containing(spawnX, tornado.getRenderBottomY(1.0F), spawnZ);
            if (!level.hasChunkAt(column)) {
                continue;
            }

            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            double spawnY = surfaceY + 4.0D + level.random.nextDouble() * 10.0D;
            double inwardPull = 0.015D + tornado.getNormalizedIntensity() * 0.035D;
            double fallSpeed = -0.035D - level.random.nextDouble() * 0.045D;
            double swirl = (level.random.nextDouble() * 2.0D - 1.0D) * 0.035D;
            level.addParticle(
                    new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DIRT.defaultBlockState()),
                    spawnX,
                    spawnY,
                    spawnZ,
                    -behindDir.x * inwardPull + lateralDir.x * swirl,
                    fallSpeed,
                    -behindDir.z * inwardPull + lateralDir.z * swirl
            );
        }
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
                    tornado.getRenderPosition(1.0F).x,
                    tornado.getRenderBottomY(1.0F),
                    tornado.getRenderPosition(1.0F).z,
                    0.0,
                    localVerticalDrift,
                    0.0
            );
        }
    }
}

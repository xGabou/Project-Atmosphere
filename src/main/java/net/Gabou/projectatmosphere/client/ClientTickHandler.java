package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.client.sound.TornadoAudioClient;
import net.Gabou.projectatmosphere.modules.wind.WindMath;
import net.Gabou.projectatmosphere.registry.ModParticles;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.Gabou.projectatmosphere.client.render.SkyEffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;

@OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ClientTickHandler {

    private static RandomSource random;

    private static int tickCounter = 0;
    private static final Set<TornadoInstance> prevTornadoes = new HashSet<>();
    private static final Set<Integer> culledRegionIds = new HashSet<>();
    private static final double CLOUD_RENDER_DISTANCE = AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get();

    private static int getRegionId(CloudRegion region) {
        return System.identityHashCode(region);
    }

    public static boolean isRegionCulled(CloudRegion region) {
        return culledRegionIds.contains(getRegionId(region));
    }


    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ClientSyncLock.isReady()) return;
        if (Minecraft.getInstance().isPaused()) return;

        SkyEffectState.beginFrame();
        tickCounter++;
        TornadoManager.tick(Minecraft.getInstance().level);
        Minecraft mc = Minecraft.getInstance();
        RainbowWeatherTracker.tick(mc);

        if (mc.level != null && mc.player != null) {
            CloudManager<?> manager = CloudManager.get(mc.level);
            List<CloudRegion> regions = manager.getCloudGenerator().getClouds();
            double playerX = mc.player.getX();
            double playerZ = mc.player.getZ();
            Set<Integer> nextCulled = new HashSet<>();
            for (CloudRegion region : regions) {
                double dx = region.getWorldX() - playerX;
                double dz = region.getWorldZ() - playerZ;
                double distSq = dx * dx + dz * dz;
                if (distSq > CLOUD_RENDER_DISTANCE * CLOUD_RENDER_DISTANCE) {
                    nextCulled.add(getRegionId(region));
                }
            }
            culledRegionIds.clear();
            culledRegionIds.addAll(nextCulled);
        }

        if (mc.level != null) {
            Set<TornadoInstance> current = new HashSet<>(TornadoManager.getActiveTornadoes());
            for (TornadoInstance tornado : current) {
                float baseVol = 0.35f + 0.45f * 0.75f;
                TornadoAudioClient.ensure(tornado, baseVol, 140f);
            }
            for (TornadoInstance t : prevTornadoes) {
                if (!current.contains(t)) {
                    TornadoAudioClient.stop(t);
                }
            }
            prevTornadoes.clear();
            prevTornadoes.addAll(current);
        }

        if (mc.level != null && mc.level.getGameTime() % 2 == 0) {
            for (TornadoInstance tornado : TornadoManager.getActiveTornadoes()) {
                TornadoRenderHandler.spawnDebrisParticles(tornado, (ClientLevel) mc.level);
            }
        }
        if (tickCounter % 40 == 0) {
            if (mc.level != null && mc.player != null) {
                // snapshot
                BlockPos pos = mc.player.blockPosition();
                long gameTime = mc.level.getGameTime();
                BiomeInstanceKey key = new BiomeInstanceKey(
                        AtmosphereUtils.getBiomeLocation(pos, mc.level), pos);
                WindVector wind = ForecastOrchestrator.getWind(key, gameTime);
                float speed = WindMath.getSmoothGustedSpeed(wind, gameTime);

                if (speed >= 2.0f) {
                    AsyncAtmosphereService.runWithCallback(
                            PoolType.CLIENT,
                            () -> computeWindSpawn(pos, wind, speed),
                            data -> {
                                if (mc.level == null) return;
                                SimpleParticleType particle = getSeasonalLeafParticle(mc.level, data.pos(), mc.level.random);
                                SimpleParticleType windStreaks = ModParticles.WIND_STREAKS.get();

                                if (particle != null) {
                                    mc.level.addParticle(particle, data.x(), data.y(), data.z(),
                                            data.vx(), data.vy(), data.vz());
                                }
                                mc.level.addParticle(windStreaks, data.x(), data.y(), data.z(),
                                        data.vx(), data.vy(), data.vz());
                            }
                    );
                }
            }
        }

    }

    public static SimpleParticleType getSeasonalLeafParticle(ClientLevel level, BlockPos pos, RandomSource random) {
        SeasonStage season = getCurrentSeason(level, pos);

        List<SimpleParticleType> candidates = switch (season) {
            case AUTUMN -> List.of(
                    ModParticles.TRIANGLE_ORANGE.get(),
                    ModParticles.TRIANGLE_JAUNE.get(),
                    ModParticles.ROUND_ORANGE.get(),
                    ModParticles.ROUND_JAUNE.get(),
                    ModParticles.HEART_ORANGE.get(),
                    ModParticles.HEART_JAUNE.get()
            );
            case SPRING, SUMMER -> List.of(
                    ModParticles.TRIANGLE_VERT.get(),
                    ModParticles.ROUND_VERT.get(),
                    ModParticles.HEART_VERT.get()
            );
            default -> List.of();
        };

        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    public static SeasonStage getCurrentSeason(ClientLevel level, BlockPos pos) {
        return SeasonTimeHelper.stage(level);
    }
    public record WindSpawnData(BlockPos pos,
                                double x, double y, double z,
                                double vx, double vy, double vz) {}

    private static WindSpawnData computeWindSpawn(BlockPos pos, WindVector wind, float speed) {
        float angle = wind.angleRadians();
        double dx = -Math.sin(angle);
        double dz = Math.cos(angle);

        double minDist = 20.0;
        double maxDist = 100.0;
        double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);
        double lateralRange = 10.0;
        double lateral = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * lateralRange;

        double perpX = -dz;
        double perpZ = dx;

        double spawnX = pos.getX() + 0.5 - dx * distance + perpX * lateral;
        double spawnY = pos.getY() + 1.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5;
        double spawnZ = pos.getZ() + 0.5 - dz * distance + perpZ * lateral;

        speed *= 0.2F;
        double vx = dx * speed;
        double vy = 0.03;
        double vz = dz * speed;

        return new WindSpawnData(pos, spawnX, spawnY, spawnZ, vx, vy, vz);
    }

}

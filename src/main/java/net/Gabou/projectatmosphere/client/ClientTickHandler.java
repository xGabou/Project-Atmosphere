package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.client.atmosphere.AtmosphereClientState;
import net.Gabou.projectatmosphere.client.fog.AtmosphereFogState;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsClientHooks;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.client.sound.WeatherAudioClient;
import net.Gabou.projectatmosphere.modules.wind.WindMath;
import net.Gabou.projectatmosphere.registry.ModParticles;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.compat.sky.AtmosphereSkyEffectController;
import net.Gabou.projectatmosphere.client.render.sky.SkyEffectState;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudFrameDiagnostics;
import net.Gabou.projectatmosphere.client.screen.TfcSeasonConflictScreen;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ClientTickHandler {

    private static RandomSource random;

    private static int tickCounter = 0;
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // Run at the title screen before normal client work or the test
        // auto-driver can launch a world with an invalid TFC season setup.
        if (TfcSeasonConflictScreen.presentIfNeeded(mc)) {
            return;
        }
        // Marker-gated test infrastructure must also run at the title screen:
        // it is responsible for creating and opening its own fresh fixture.
        // The call is inert during normal play.
        VolumetricCloudFrameDiagnostics.tickT132AutoDriver();
        boolean simpleCloudsLoaded = AtmosphereCloudServices.isSimpleCloudsLoaded();
        if (simpleCloudsLoaded) {
            ClientHurricaneStateCache.tick(mc.level);
        }
        if (mc.level == null) {
            if (simpleCloudsLoaded) {
                SimpleCloudsClientHooks.clearTornadoes();
            }
            WeatherAudioClient.stopAll();
            return;
        }
        if (mc.isPaused()) return;

        if (simpleCloudsLoaded) {
            SimpleCloudsClientHooks.tickTornadoes(mc.level, tickCounter);
        }
        if (!ClientSyncLock.isReady()) return;

        AtmosphereClientState.tick(mc);
        AtmosphereFogState.tick(mc);
        WeatherAudioClient.tick(mc);

        SkyEffectState.beginFrame();
        tickCounter++;
        AtmosphereSkyEffectController.tick(mc);

        if (tickCounter % 40 == 0) {
            if (mc.player != null) {
                if (mc.level != null && simpleCloudsLoaded) {
                    SimpleCloudsClientHooks.logCloudDiagnostic(mc.player.getX(), mc.player.getZ(), mc.level);
                }

                // snapshot
                BlockPos pos = mc.player.blockPosition();
                long gameTime = mc.level.getGameTime();
                WindVector wind = ForecastOrchestrator.getWind(pos, gameTime);
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

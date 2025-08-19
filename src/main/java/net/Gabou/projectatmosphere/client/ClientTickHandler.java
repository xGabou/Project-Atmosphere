package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.TornadoRenderHandler;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ClientTickHandler {

    private static RandomSource random;

    private static int tickCounter = 0;
    private static final Set<TornadoInstance> prevTornadoes = new HashSet<>();

    private final ProjectAtmosphere.SystemProfile systemProfile = ProjectAtmosphere.SystemProfile.create(true) ;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ClientSyncLock.isReady()) return;
        if (Minecraft.getInstance().isPaused()) return;

        tickCounter++;
        TornadoManager.tick(Minecraft.getInstance().level);
        Minecraft mc = Minecraft.getInstance();

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
        if (tickCounter % 40 != 0) return; 
        AsyncAtmosphereService.runClient(() -> {
            if (mc.level == null || mc.player == null) return;
            if (random == null) {
                random = mc.level.random;
            }
            BlockPos pos = mc.player.blockPosition();
            BiomeInstanceKey key = new BiomeInstanceKey(
                    AtmosphereUtils.getBiomeLocation(pos, mc.level), pos);
            if (key == null) return;

            long gameTime = mc.level.getGameTime();
            WindVector wind = ForecastOrchestrator.getCurrentWind(key, gameTime);
            float speed = WindMath.getSmoothGustedSpeed(wind,gameTime);
            if (speed < 2.0f) return;

            float angle = wind.angleRadians();
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);

            SimpleParticleType particle = getSeasonalLeafParticle(mc.level, pos, mc.level.random);
            if (particle != null) {
                
                
                double minDist = 20.0;
                double maxDist = 100.0;
                double distance = minDist + mc.level.random.nextDouble() * (maxDist - minDist);
                double lateralRange = 10.0;
                double lateral = (mc.level.random.nextDouble() * 2.0 - 1.0) * lateralRange;

                
                double perpX = -dz;
                double perpZ = dx;

                double spawnX = pos.getX() + 0.5 - dx * distance + perpX * lateral;
                double spawnY = pos.getY() + 1.5 + (random.nextDouble() - 0.5) * 0.5;
                double spawnZ = pos.getZ() + 0.5 - dz * distance + perpZ * lateral;


                
                speed *= 0.2F;
                double vx = dx * speed;
                double vy = 0.03;
                double vz = dz * speed;

                mc.level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
            }
        });
    }

    public static SimpleParticleType getSeasonalLeafParticle(ClientLevel level, BlockPos pos, RandomSource random) {
        Season season = getCurrentSeason(level, pos);

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

    public static Season getCurrentSeason(ClientLevel level, BlockPos pos) {
        return SeasonHelper.getSeasonState(level).getSeason();
    }
}

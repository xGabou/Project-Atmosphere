package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.wind.util.WindProfileManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static net.Gabou.projectatmosphere.util.AtmosphereUtils.getSeasonalLeafParticle;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ClientTickHandler {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;
        if (!ClientSyncLock.isReady()) return;
        tickCounter++;
        if (tickCounter % 40 != 0) return; // Run every 20 ticks (1 second)
        AsyncAtmosphereService.runClient(() -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BlockPos pos = mc.player.blockPosition();
        BiomeInstanceKey key = AtmosphereUtils.findNearestBiomeInstanceKeyWithNoMap(
                AtmosphereUtils.getBiomeLocation(pos, mc.level), pos);
        if (key == null) return;

        WindVector wind = WindProfileManager.getCurrentWind(key, mc.level.getDayTime());
        float speed = wind.speed();
        if (speed < 2.0f) return;

        float angle = wind.angleRadians();
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);

        SimpleParticleType particle = getSeasonalLeafParticle(mc.level, pos, mc.level.random);
        if (particle != null) {
            // Spawn behind the player relative to wind direction
            // Spawn at least 20 blocks away, up to 25 blocks, with a small sideways spread
            double minDist = 20.0;
            double maxDist = 35.0;
            double distance = minDist + mc.level.random.nextDouble() * (maxDist - minDist);
            double lateralRange = 3.0;
            double lateral = (mc.level.random.nextDouble() * 2.0 - 1.0) * lateralRange;

            // perp vector to wind dir
            double perpX = -dz;
            double perpZ = dx;

            double spawnX = pos.getX() + 0.5 - dx * distance + perpX * lateral;
            double spawnY = pos.getY() + 1.5 + (mc.level.random.nextDouble() - 0.5) * 0.5;
            double spawnZ = pos.getZ() + 0.5 - dz * distance + perpZ * lateral;


            // Particle velocity following the wind
            double vx = dx * speed;
            double vy = 0.03;
            double vz = dz * speed;

            mc.level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
        }
        });
    }
}

package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.wind.util.WindProfileManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static net.Gabou.projectatmosphere.util.AtmosphereUtils.getSeasonalLeafParticle;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ClientTickHandler {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BlockPos pos = mc.player.blockPosition();
        BiomeInstanceKey key = AtmosphereUtils.findNearestBiomeInstanceKeyWithNoMap(
               AtmosphereUtils.getBiomeLocation(pos, mc.level),pos);
        if (key == null) return;

        WindVector wind = WindProfileManager.getCurrentWind(key, mc.level.getDayTime());
        float speed = wind.speed();
        if (speed < 2.0f || mc.level.random.nextFloat() > 0.1f) return;

        float angle = wind.angleRadians();
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);

        SimpleParticleType particle = getSeasonalLeafParticle(mc.level, pos, mc.level.random);
        if (particle != null) {
            double x = pos.getX() + mc.level.random.nextDouble();
            double y = pos.getY() + 1.5;
            double z = pos.getZ() + mc.level.random.nextDouble();

            double s = 0.1 + speed * 0.02;
            mc.level.addParticle(particle, x, y, z, dx * s, 0.03, dz * s);
        }
    }


}

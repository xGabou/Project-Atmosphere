package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.particles.DebrisParticle;
import net.Gabou.projectatmosphere.particles.WindLeafParticle;
import net.Gabou.projectatmosphere.particles.WindStreakParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModClient {

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        register(event, ModParticles.TRIANGLE_VERT);
        register(event, ModParticles.TRIANGLE_JAUNE);
        register(event, ModParticles.TRIANGLE_ORANGE);
        register(event, ModParticles.ROUND_VERT);
        register(event, ModParticles.ROUND_JAUNE);
        register(event, ModParticles.ROUND_ORANGE);
        register(event, ModParticles.HEART_VERT);
        register(event, ModParticles.HEART_JAUNE);
        register(event, ModParticles.HEART_ORANGE);
        event.registerSpriteSet(ModParticles.DEBRIS.get(), DebrisParticle.Provider::new);
        event.registerSpriteSet(ModParticles.WIND_STREAKS.get(), WindStreakParticle.Provider::new);
    }

    private static void register(RegisterParticleProvidersEvent event, RegistryObject<SimpleParticleType> particleType) {
        event.registerSpriteSet(particleType.get(), WindLeafParticle.Provider::new);
    }
}

package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.particles.WindLeafParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModClient {

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.TRIANGLE_VERT.get(), WindLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.TRIANGLE_ORANGE.get(), WindLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.TRIANGLE_JAUNE.get(), WindLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.ROUND_VERT.get(), WindLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.ROUND_ORANGE.get(), WindLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.ROUND_JAUNE.get(), WindLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.HEART_VERT.get(), WindLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.HEART_ORANGE.get(), WindLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.HEART_JAUNE.get(), WindLeafParticle.Provider::new);
    }
}

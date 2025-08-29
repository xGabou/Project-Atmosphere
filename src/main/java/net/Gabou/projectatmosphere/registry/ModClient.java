package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.particles.DebrisParticle;
import net.Gabou.projectatmosphere.particles.WindLeafParticle;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModClient {

    public static void register(IEventBus modEventBus) {
        // Hook into client mod bus
        modEventBus.addListener(ModClient::onRegisterParticles);
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        // Simple leaf-style particles
        register(event, ModParticles.TRIANGLE_VERT);
        register(event, ModParticles.TRIANGLE_JAUNE);
        register(event, ModParticles.TRIANGLE_ORANGE);
        register(event, ModParticles.ROUND_VERT);
        register(event, ModParticles.ROUND_JAUNE);
        register(event, ModParticles.ROUND_ORANGE);
        register(event, ModParticles.HEART_VERT);
        register(event, ModParticles.HEART_JAUNE);
        register(event, ModParticles.HEART_ORANGE);

        // Custom debris particle
        event.registerSpriteSet(ModParticles.DEBRIS.get(), DebrisParticle.Provider::new);
    }

    private static void register(RegisterParticleProvidersEvent event,
                                 DeferredHolder<ParticleType<?>, SimpleParticleType> particleType) {
        event.registerSpriteSet(particleType.get(), WindLeafParticle.Provider::new);
    }
}

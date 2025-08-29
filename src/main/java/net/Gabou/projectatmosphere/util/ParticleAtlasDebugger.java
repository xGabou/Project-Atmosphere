package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

import java.util.Set;

public class ParticleAtlasDebugger {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ParticleAtlasDebugger::onStitch);
    }

    private static void onStitch(TextureAtlasStitchedEvent event) {
        if (!event.getAtlas().location().equals(TextureAtlas.LOCATION_PARTICLES)) return;

        Set<ResourceLocation> loaded = event.getAtlas().getTextures().keySet();
        ProjectAtmosphere.LOGGER.info(">>> Particle atlas contains {} sprites:", loaded.size());
        loaded.forEach(loc -> ProjectAtmosphere.LOGGER.info("  - {}", loc));
    }
}

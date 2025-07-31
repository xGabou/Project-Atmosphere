package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public class ParticleAtlasDebugger {
    @SubscribeEvent
    public static void onStitch(TextureStitchEvent.Post event) {
        if (!event.getAtlas().location().equals(TextureAtlas.LOCATION_PARTICLES)) return;
        Set<ResourceLocation> loaded = event.getAtlas().getTextureLocations();
        ProjectAtmosphere.LOGGER.info(">>> Particle atlas contains {} sprites:", loaded.size());
        loaded.forEach(loc -> ProjectAtmosphere.LOGGER.info("  - {}", loc));
    }
}

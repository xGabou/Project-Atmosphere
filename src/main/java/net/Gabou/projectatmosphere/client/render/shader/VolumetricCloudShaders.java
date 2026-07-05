package net.Gabou.projectatmosphere.client.render.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/**
 * Registers the volumetric cloud shader programs: weather-map splat, global
 * raymarch, shadow map generation, and the ground shadow apply pass.
 */
@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VolumetricCloudShaders {
    private static final ResourceLocation SPLAT_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_weather_splat");
    private static final ResourceLocation VOLUME_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_atmosphere_volume");
    private static final ResourceLocation SHADOW_MAP_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_shadow_map");
    private static final ResourceLocation SHADOW_APPLY_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_shadow_apply");

    private static ShaderInstance splatShader;
    private static ShaderInstance volumeShader;
    private static ShaderInstance shadowMapShader;
    private static ShaderInstance shadowApplyShader;

    private VolumetricCloudShaders() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SPLAT_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> splatShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), VOLUME_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> volumeShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADOW_MAP_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> shadowMapShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADOW_APPLY_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> shadowApplyShader = loaded);
        // Reloaded programs render into fresh state; stale history would blend
        // pre-reload frames into the new output.
        net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderer.invalidateHistory();
        ProjectAtmosphere.LOGGER.info("[VolumetricClouds] shader programs registered");
    }

    public static ShaderInstance splatShader() {
        return splatShader;
    }

    public static ShaderInstance volumeShader() {
        return volumeShader;
    }

    public static ShaderInstance shadowMapShader() {
        return shadowMapShader;
    }

    public static ShaderInstance shadowApplyShader() {
        return shadowApplyShader;
    }

    public static boolean isReady() {
        return splatShader != null && volumeShader != null;
    }
}

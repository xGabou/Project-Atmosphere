package net.Gabou.projectatmosphere.client.render.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.io.IOException;

/**
 * Registers the first CloudField snapshot volume shader. This stays separate
 * from the live PA cloud renderer and from the Simple Clouds compatibility
 * renderers.
 */
@EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CloudFieldVolumeShaders {
    private static final ResourceLocation SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_field_volume");
    private static final ResourceLocation COMPOSITE_SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_field_composite");

    private static ShaderInstance shader;
    private static ShaderInstance compositeShader;

    private CloudFieldVolumeShaders() {
    }

    /**
     * Registers the CloudField volume prototype shader during resource reload.
     *
     * @param event Forge shader registration event
     * @throws IOException when the shader resource cannot be loaded
     */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER_ID, DefaultVertexFormat.POSITION_TEX), loaded -> {
            shader = loaded;
            ProjectAtmosphere.LOGGER.info("[CloudFieldVolume] shaderReload.ready shader={}", SHADER_ID);
        });
        event.registerShader(new ShaderInstance(event.getResourceProvider(), COMPOSITE_SHADER_ID, DefaultVertexFormat.POSITION_TEX), loaded -> {
            compositeShader = loaded;
            ProjectAtmosphere.LOGGER.info("[CloudFieldVolume] shaderReload.ready composite={}", COMPOSITE_SHADER_ID);
        });
    }

    /**
     * Returns the CloudField volume shader, or null if resource loading failed.
     *
     * @return loaded shader instance, or null
     */
    public static ShaderInstance getShader() {
        return shader;
    }

    public static ShaderInstance getCompositeShader() {
        return compositeShader;
    }

    /**
     * Reports whether the CloudField volume shader is available this frame.
     *
     * @return true when the shader has been registered successfully
     */
    public static boolean isReady() {
        return shader != null && compositeShader != null;
    }
}

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
 * Registers the first CloudField snapshot volume shader. This stays separate
 * from the live PA cloud renderer and from the Simple Clouds compatibility
 * renderers.
 */
@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CloudFieldVolumeShaders {
    private static final ResourceLocation SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_field_volume");

    private static ShaderInstance shader;

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
    }

    /**
     * Returns the CloudField volume shader, or null if resource loading failed.
     *
     * @return loaded shader instance, or null
     */
    public static ShaderInstance getShader() {
        return shader;
    }

    /**
     * Reports whether the CloudField volume shader is available this frame.
     *
     * @return true when the shader has been registered successfully
     */
    public static boolean isReady() {
        return shader != null;
    }
}

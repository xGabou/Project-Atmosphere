package net.Gabou.projectatmosphere.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TornadoShaders {
    public static final ResourceLocation TORNADO_TEXTURE = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "textures/effects/tornado.png");
    public static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "textures/effects/base.png");
    public static final ResourceLocation NOISE_TEXTURE = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "textures/effects/noise.png");
    public static final ResourceLocation FLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "textures/effects/flowmap.png");

    private static final ResourceLocation SHADER_ID = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "tornado_round");

    private static ShaderInstance shader;

    private TornadoShaders() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER_ID, DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
    }

    public static ShaderInstance getShader() {
        return shader;
    }

    public static boolean isReady() {
        return shader != null;
    }
}

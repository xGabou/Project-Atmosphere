package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.io.IOException;

public class TornadoShaders {
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(TornadoShaders::onRegisterShaders);
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance tornado = new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "tornado"),
                    DefaultVertexFormat.POSITION_TEX
            );
            event.registerShader(tornado, shader -> MyShaders.TORNADO = shader);

            ShaderInstance boxTornado = new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "box_tornado"),
                    DefaultVertexFormat.POSITION_TEX
            );
            event.registerShader(boxTornado, shader -> MyShaders.BOX_TORNADO = shader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load tornado shaders", e);
        }
    }
}

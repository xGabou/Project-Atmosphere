package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TornadoShaders {


    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        ShaderInstance shader = new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "tornado"), DefaultVertexFormat.POSITION_TEX);
        event.registerShader(shader, s -> MyShaders.TORNADO = s);
        ShaderInstance shader1 = new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "box_tornado"), DefaultVertexFormat.POSITION_TEX);
        event.registerShader(shader1, s -> MyShaders.BOX_TORNADO = s);
    }


}

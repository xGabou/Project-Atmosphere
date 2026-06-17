package net.Gabou.projectatmosphere.client.render.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HurricaneShaders {
    public static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "textures/effects/base.png");
    public static final ResourceLocation NOISE_TEXTURE = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "textures/effects/noise.png");
    public static final ResourceLocation FLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "textures/effects/flowmap.png");

    private static final ResourceLocation OPAQUE_SHADER_ID = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "hurricane_clouds");
    private static final ResourceLocation OPAQUE_MASK_SHADER_ID = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "hurricane_eye_mask");
    private static final ResourceLocation TRANSPARENCY_SHADER_ID = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "hurricane_clouds_transparency");
    private static final ResourceLocation TRANSPARENCY_MASK_SHADER_ID = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "hurricane_eye_mask_transparency");

    private static ShaderInstance opaqueShader;
    private static ShaderInstance opaqueMaskShader;
    private static ShaderInstance transparencyShader;
    private static ShaderInstance transparencyMaskShader;

    private HurricaneShaders() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        if (!ModList.get().isLoaded("simpleclouds")) {
            opaqueShader = null;
            opaqueMaskShader = null;
            transparencyShader = null;
            transparencyMaskShader = null;
            return;
        }
        event.registerShader(new ShaderInstance(event.getResourceProvider(), OPAQUE_SHADER_ID, DefaultVertexFormat.POSITION_TEX), loaded -> opaqueShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), OPAQUE_MASK_SHADER_ID, DefaultVertexFormat.POSITION_TEX), loaded -> opaqueMaskShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), TRANSPARENCY_SHADER_ID, DefaultVertexFormat.POSITION_TEX), loaded -> transparencyShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), TRANSPARENCY_MASK_SHADER_ID, DefaultVertexFormat.POSITION_TEX), loaded -> transparencyMaskShader = loaded);
    }

    public static ShaderInstance getOpaqueShader() {
        return opaqueShader;
    }

    public static ShaderInstance getOpaqueMaskShader() {
        return opaqueMaskShader;
    }

    public static ShaderInstance getTransparencyShader() {
        return transparencyShader;
    }

    public static ShaderInstance getTransparencyMaskShader() {
        return transparencyMaskShader;
    }

    public static boolean isOpaqueReady() {
        return opaqueShader != null && opaqueMaskShader != null;
    }

    public static boolean isTransparencyReady() {
        return transparencyShader != null && transparencyMaskShader != null;
    }
}

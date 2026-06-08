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
 * Charge le shader dedie au rendu live des nuages PA.
 * Ce shader reste separe des rendus debug et des passes tornado/hurricane.
 */
@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CloudShaders {

    private static final ResourceLocation SHADER_ID = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_volume");
    private static final ResourceLocation COMPOSITE_SHADER_ID = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_composite");

    private static ShaderInstance cloudShader;
    private static ShaderInstance compositeShader;

    private CloudShaders() {

    }

    /**
     * Enregistre le shader live des nuages.
     *
     * @param event evenement Forge de registration des shaders
     * @throws IOException si le chargement du shader echoue
     */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER_ID, DefaultVertexFormat.POSITION_TEX), loaded -> cloudShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), COMPOSITE_SHADER_ID, DefaultVertexFormat.POSITION_TEX), loaded -> compositeShader = loaded);
    }

    /**
     * Retourne le shader live des nuages.
     *
     * @return shader live, ou null si le chargement a echoue
     */
    public static ShaderInstance getShader() {
        return cloudShader;
    }

    public static ShaderInstance getCompositeShader() {
        return compositeShader;
    }

    /**
     * Indique si le shader live est pret.
     *
     * @return true si le shader a ete charge
     */
    public static boolean isReady() {
        return cloudShader != null;
    }
}

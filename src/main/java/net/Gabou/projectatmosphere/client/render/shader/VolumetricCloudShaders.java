package net.Gabou.projectatmosphere.client.render.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.CoreCostDiagnosticProgram;
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
    private static final ResourceLocation MORPHOLOGY_SPLAT_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_weather_morphology");
    private static final ResourceLocation CUMULUS_LAYER_SPLAT_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_weather_cumulus_layers");
    private static final ResourceLocation VOLUME_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_atmosphere_volume");
    /**
     * T161: the compile-time-specialized FINAL program, generated from the same
     * source by the {@code generateLeanFinalShader} Gradle task.
     */
    private static final ResourceLocation LEAN_FINAL_VOLUME_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_final");
    private static final ResourceLocation SHADOW_MAP_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_shadow_map");
    private static final ResourceLocation SHADOW_APPLY_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_shadow_apply");

    private static ShaderInstance splatShader;
    private static ShaderInstance morphologySplatShader;
    private static ShaderInstance cumulusLayerSplatShader;
    private static ShaderInstance volumeShader;
    private static ShaderInstance leanFinalVolumeShader;
    private static ShaderInstance shadowMapShader;
    private static ShaderInstance shadowApplyShader;

    private VolumetricCloudShaders() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SPLAT_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> splatShader = loaded);
        event.registerShader(new ShaderInstance(
                        event.getResourceProvider(), MORPHOLOGY_SPLAT_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> morphologySplatShader = loaded);
        event.registerShader(new ShaderInstance(
                        event.getResourceProvider(), CUMULUS_LAYER_SPLAT_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> cumulusLayerSplatShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), VOLUME_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> volumeShader = loaded);
        // Registered defensively. If the generated program is missing or fails
        // to compile, that must not abort the registration of the other cloud
        // programs: the renderer's own guard then refuses to draw FINAL with
        // the monolith and session-disables with a diagnostic status, which is
        // a far more legible failure than an unexplained loss of every cloud
        // shader. leanFinalVolumeShader simply stays null.
        try {
            event.registerShader(new ShaderInstance(
                            event.getResourceProvider(), LEAN_FINAL_VOLUME_ID,
                            DefaultVertexFormat.POSITION_TEX),
                    loaded -> leanFinalVolumeShader = loaded);
        } catch (IOException | RuntimeException failure) {
            leanFinalVolumeShader = null;
            ProjectAtmosphere.LOGGER.error(
                    "[VolumetricClouds] lean FINAL program {} failed to load;"
                            + " the volumetric pass will session-disable rather than"
                            + " silently render FINAL with the diagnostic program",
                    LEAN_FINAL_VOLUME_ID, failure);
        }
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADOW_MAP_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> shadowMapShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADOW_APPLY_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> shadowApplyShader = loaded);
        // Reloaded programs render into fresh state; stale history would blend
        // pre-reload frames into the new output.
        net.Gabou.projectatmosphere.clouds.client.render.volumetric.CloudWeatherMapRenderer.invalidateCache();
        net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderer.invalidateHistory();
        ProjectAtmosphere.LOGGER.info("[VolumetricClouds] shader programs registered");
    }

    public static ShaderInstance splatShader() {
        return splatShader;
    }

    /** The unmodified program retaining every dormant diagnostic path. */
    public static ShaderInstance volumeShader() {
        return volumeShader;
    }

    /**
     * Returns the program for {@code program}, or null when it failed to link.
     *
     * <p>A null lean program is never silently replaced by the monolith: the
     * caller session-disables instead, because binding the monolith for FINAL
     * would quietly restore the pre-T161 cost while still producing the right
     * image, which is precisely the regression this split exists to prevent.
     */
    public static ShaderInstance volumeShader(CoreCostDiagnosticProgram program) {
        if (program == null) {
            return volumeShader;
        }
        return switch (program) {
            case DIAGNOSTIC_MONOLITH -> volumeShader;
            case LEAN_FINAL -> leanFinalVolumeShader;
        };
    }

    /** True when the separately linked lean FINAL program is available. */
    public static boolean leanFinalShaderReady() {
        return leanFinalVolumeShader != null;
    }

    public static ShaderInstance morphologySplatShader() {
        return morphologySplatShader;
    }

    public static ShaderInstance cumulusLayerSplatShader() {
        return cumulusLayerSplatShader;
    }

    public static ShaderInstance shadowMapShader() {
        return shadowMapShader;
    }

    public static ShaderInstance shadowApplyShader() {
        return shadowApplyShader;
    }

    public static boolean isReady() {
        return splatShader != null
                && morphologySplatShader != null
                && cumulusLayerSplatShader != null
                && volumeShader != null;
    }
}

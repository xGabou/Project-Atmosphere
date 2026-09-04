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
    /**
     * T140 diagnostic programs. Each is the lean renderer plus the whole-pixel
     * rejection oracle at a different granularity; the mask renders the
     * oracle's verdict instead of the scene.
     */
    private static final ResourceLocation T140_PIXEL_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t140_pixel");
    private static final ResourceLocation T140_MASK_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t140_mask");
    private static final ResourceLocation T140_TILE8_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t140_tile8");
    private static final ResourceLocation T140_TILE16_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t140_tile16");
    /** T162 attribution arms; see CoreCostDiagnosticProgram. */
    private static final ResourceLocation T162_NO_LIGHT_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t162_nolight");
    private static final ResourceLocation T162_NO_RAIN_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t162_norain");
    private static final ResourceLocation T162_FW1_ADDRESS_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t162_fw1_address");
    private static final ResourceLocation T162_FW2_CANDIDATE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t162_fw2_candidate");
    private static final ResourceLocation T162_FW3_DESCRIPTOR_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t162_fw3_descriptor");
    private static final ResourceLocation T162_FW4_SHAPE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t162_fw4_shape");
    private static final ResourceLocation T162_FW5_NODETAIL_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t162_fw5_nodetail");
    private static final ResourceLocation T162_FW6_NORAIN_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t162_fw6_norain");
    private static final ResourceLocation T162_FW7_DENSITY_ID =
            ResourceLocation.fromNamespaceAndPath(
                    ProjectAtmosphere.MODID, "cloud_atmosphere_volume_t162_fw7_density");
    private static final ResourceLocation SHADOW_MAP_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_shadow_map");
    private static final ResourceLocation SHADOW_APPLY_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_shadow_apply");

    private static ShaderInstance splatShader;
    private static ShaderInstance morphologySplatShader;
    private static ShaderInstance cumulusLayerSplatShader;
    private static ShaderInstance volumeShader;
    private static ShaderInstance leanFinalVolumeShader;
    private static ShaderInstance t140PixelShader;
    private static ShaderInstance t140MaskShader;
    private static ShaderInstance t140Tile8Shader;
    private static ShaderInstance t140Tile16Shader;
    private static ShaderInstance t162NoLightShader;
    private static ShaderInstance t162NoRainShader;
    private static ShaderInstance t162Fw1Shader;
    private static ShaderInstance t162Fw2Shader;
    private static ShaderInstance t162Fw3Shader;
    private static ShaderInstance t162Fw4Shader;
    private static ShaderInstance t162Fw5Shader;
    private static ShaderInstance t162Fw6Shader;
    private static ShaderInstance t162Fw7Shader;
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
            registerDiagnosticVolumeProgram(event, T140_PIXEL_ID,
                    loaded -> t140PixelShader = loaded);
            registerDiagnosticVolumeProgram(event, T140_MASK_ID,
                    loaded -> t140MaskShader = loaded);
            registerDiagnosticVolumeProgram(event, T140_TILE8_ID,
                    loaded -> t140Tile8Shader = loaded);
            registerDiagnosticVolumeProgram(event, T140_TILE16_ID,
                    loaded -> t140Tile16Shader = loaded);
            registerDiagnosticVolumeProgram(event, T162_NO_LIGHT_ID,
                    loaded -> t162NoLightShader = loaded);
            registerDiagnosticVolumeProgram(event, T162_NO_RAIN_ID,
                    loaded -> t162NoRainShader = loaded);
            registerDiagnosticVolumeProgram(event, T162_FW1_ADDRESS_ID,
                    loaded -> t162Fw1Shader = loaded);
            registerDiagnosticVolumeProgram(event, T162_FW2_CANDIDATE_ID,
                    loaded -> t162Fw2Shader = loaded);
            registerDiagnosticVolumeProgram(event, T162_FW3_DESCRIPTOR_ID,
                    loaded -> t162Fw3Shader = loaded);
            registerDiagnosticVolumeProgram(event, T162_FW4_SHAPE_ID,
                    loaded -> t162Fw4Shader = loaded);
            registerDiagnosticVolumeProgram(event, T162_FW5_NODETAIL_ID,
                    loaded -> t162Fw5Shader = loaded);
            registerDiagnosticVolumeProgram(event, T162_FW6_NORAIN_ID,
                    loaded -> t162Fw6Shader = loaded);
            registerDiagnosticVolumeProgram(event, T162_FW7_DENSITY_ID,
                    loaded -> t162Fw7Shader = loaded);
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
            case T140_PIXEL_ORACLE -> t140PixelShader;
            case T140_MASK -> t140MaskShader;
            case T140_TILE8 -> t140Tile8Shader;
            case T140_TILE16 -> t140Tile16Shader;
            case T162_NO_LIGHT -> t162NoLightShader;
            case T162_NO_RAIN -> t162NoRainShader;
            case T162_FW1_ADDRESS -> t162Fw1Shader;
            case T162_FW2_CANDIDATE -> t162Fw2Shader;
            case T162_FW3_DESCRIPTOR -> t162Fw3Shader;
            case T162_FW4_SHAPE -> t162Fw4Shader;
            case T162_FW5_NODETAIL -> t162Fw5Shader;
            case T162_FW6_NORAIN -> t162Fw6Shader;
            case T162_FW7_DENSITY -> t162Fw7Shader;
        };
    }

    /**
     * Registers one T140 diagnostic program. A diagnostic program that fails to
     * build must not take the renderer down with it, so the failure is logged
     * and the slot left null; the campaign that selects it reports the missing
     * program instead.
     */
    private static void registerDiagnosticVolumeProgram(
            RegisterShadersEvent event,
            ResourceLocation id,
            java.util.function.Consumer<ShaderInstance> sink) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), id,
                            DefaultVertexFormat.POSITION_TEX),
                    sink::accept);
        } catch (IOException | RuntimeException failure) {
            ProjectAtmosphere.LOGGER.error(
                    "[VolumetricClouds] T140 diagnostic program {} failed to load", id, failure);
        }
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

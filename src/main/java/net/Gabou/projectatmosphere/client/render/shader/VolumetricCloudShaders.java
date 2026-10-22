package net.Gabou.projectatmosphere.client.render.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.CoreCostDiagnosticProgram;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormCampaignRegistry;
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
    private static final ResourceLocation COVERAGE_BLUR_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_coverage_blur");
    private static final ResourceLocation SHADOW_MAP_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_shadow_map");
    private static final ResourceLocation SHADOW_APPLY_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_shadow_apply");

    private static ShaderInstance splatShader;
    private static ShaderInstance morphologySplatShader;
    private static ShaderInstance cumulusLayerSplatShader;
    private static ShaderInstance coverageBlurShader;
    private static ShaderInstance volumeShader;
    private static ShaderInstance leanFinalVolumeShader;
    /**
     * The generated builds, keyed by the program that selects them: the
     * campaign arms, loaded only while their campaign is armed, and (T206)
     * the two production programs FINAL draws with, loaded always. There are
     * several dozen; a field, a ResourceLocation, a registration line and a
     * switch case per program meant every attribution campaign added four
     * more places to forget.
     */
    private static final java.util.Map<CoreCostDiagnosticProgram, ShaderInstance>
            diagnosticVolumeShaders =
                    new java.util.EnumMap<>(CoreCostDiagnosticProgram.class);
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
        event.registerShader(new ShaderInstance(
                        event.getResourceProvider(), COVERAGE_BLUR_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> coverageBlurShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), VOLUME_ID, DefaultVertexFormat.POSITION_TEX),
                loaded -> {
                    volumeShader = loaded;
                    logProgramBinarySize("diagnostic_monolith", loaded);
                });
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
                    loaded -> {
                        leanFinalVolumeShader = loaded;
                        logProgramBinarySize("lean_final", loaded);
                    });
            diagnosticVolumeShaders.clear();
            // T206. FINAL's frame is three programs: the gate build and the
            // rain pass draw before the split cloud program. They register
            // unconditionally and inside this try: a link failure of either
            // propagates to the catch below, which nulls the lean program so
            // the renderer session-disables with the same loud status a
            // failed FINAL link produces - never a FINAL frame without rain,
            // never the monolith in its place.
            int productionPrograms = 2;
            for (CoreCostDiagnosticProgram program : CoreCostDiagnosticProgram.values()) {
                if (program.finalSupportProgram()) {
                    registerProductionVolumeProgram(event, program);
                    productionPrograms++;
                }
            }
            // T191. Campaign arms are loaded only while their campaign is
            // armed. Every arm ever measured used to be compiled and linked on
            // every startup - 139 full-size fragment programs by T190 - which
            // is minutes of compilation and enough memory pressure that one
            // launch died in the middle of it. Nothing about a historical
            // experiment requires it to be a live program on a run that cannot
            // select it.
            //
            // The source, the generated variants and the evidence all stay in
            // the tree; only the ShaderInstance is conditional.
            java.util.Set<String> activeCampaigns =
                    StormCampaignRegistry.activeCampaignIds();
            // A campaign's arms are not confined to its own prefix: matrices
            // reuse earlier campaigns' programs as controls. Registering only
            // the prefix match leaves those controls unloaded, which the
            // renderer can only report as a missing program mid-sweep.
            java.util.Set<CoreCostDiagnosticProgram> selectable =
                    StormCampaignRegistry.programsSelectableByActiveCampaigns();
            int registered = 0;
            int skipped = 0;
            for (CoreCostDiagnosticProgram program : CoreCostDiagnosticProgram.values()) {
                if (program.isProductionProgram()) {
                    continue;
                }
                if (program.fixtureOnlyProgram()) {
                    if ("1".equals(System.getenv("PA_T255_CAPTURE")) || "1".equals(System.getenv("PA_T256_CAPTURE"))
                            || "1".equals(System.getenv("PA_T258_CAPTURE"))
                            || "1".equals(System.getenv("PA_T260_CAPTURE"))
                            || "1".equals(System.getenv("PA_T257_CAPTURE"))
                            || net.Gabou.projectatmosphere.clouds.client.render.volumetric.ExperimentalCloudPreview.enabled()) {
                        registerDiagnosticVolumeProgram(event, program);
                        registered++;
                    } else {
                        skipped++;
                    }
                    continue;
                }
                if (!activeCampaigns.contains(program.campaignId())
                        && !selectable.contains(program)) {
                    skipped++;
                    continue;
                }
                registerDiagnosticVolumeProgram(event, program);
                registered++;
            }
            ProjectAtmosphere.LOGGER.info(
                    "T191_VARIANT_SCOPE declared={} activeCampaigns={} registered={}"
                            + " skipped={} crossCampaignControls={}"
                            + " productionAlwaysLoaded={}",
                    CoreCostDiagnosticProgram.values().length - productionPrograms,
                    activeCampaigns.isEmpty() ? "none" : String.join(",", activeCampaigns),
                    registered, skipped, selectable.size(), productionPrograms);
        } catch (IOException | RuntimeException failure) {
            leanFinalVolumeShader = null;
            diagnosticVolumeShaders.remove(CoreCostDiagnosticProgram.RAIN_PASS);
            diagnosticVolumeShaders.remove(CoreCostDiagnosticProgram.RAIN_GATE_BUILD);
            ProjectAtmosphere.LOGGER.error(
                    "[VolumetricClouds] a production cloud program failed to load"
                            + " (FINAL {} or one of its rain programs);"
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

    /** T253. Pre-blurs the nimbostratus deck coverage once per weather-map rebuild. */
    public static ShaderInstance coverageBlurShader() {
        return coverageBlurShader;
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
            default -> diagnosticVolumeShaders.get(program);
        };
    }

    /**
     * T191. Why a diagnostic program is unavailable, for the renderer's status
     * line. A campaign arm selected without its marker is a setup mistake with
     * an obvious fix, and it must not read as a compile failure.
     */
    public static String missingProgramReason(CoreCostDiagnosticProgram program) {
        if (program == null || volumeShader(program) != null) {
            return "loaded";
        }
        if (program.isProductionProgram()) {
            return "production_program_failed_to_load";
        }
        String campaign = program.campaignId();
        return StormCampaignRegistry.activeCampaignIds().contains(campaign)
                ? "campaign_" + campaign + "_active_but_program_failed_to_load"
                : "campaign_" + campaign + "_not_armed:"
                        + " create its marker file and restart the client";
    }

    /**
     * T206. Registers one production program FINAL's frame draws with. Unlike
     * a diagnostic program, a failure here is not caught: it propagates to the
     * caller's catch, which takes FINAL down with it. A FINAL frame drawn
     * without its rain pass would be a frame without rain, and the monolith
     * is never the fallback.
     */
    private static void registerProductionVolumeProgram(
            RegisterShadersEvent event,
            CoreCostDiagnosticProgram program) throws IOException {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                ProjectAtmosphere.MODID, program.resourceName());
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), id,
                        DefaultVertexFormat.POSITION_TEX),
                loaded -> {
                    diagnosticVolumeShaders.put(program, loaded);
                    logProgramBinarySize(program.serializedName(), loaded);
                });
    }

    /**
     * Registers one generated diagnostic program. A diagnostic program that
     * fails to build must not take the renderer down with it, so the failure
     * is logged and the slot left empty; the campaign that selects it reports
     * the missing program instead.
     */
    private static void registerDiagnosticVolumeProgram(
            RegisterShadersEvent event,
            CoreCostDiagnosticProgram program) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                ProjectAtmosphere.MODID, program.resourceName());
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), id,
                            DefaultVertexFormat.POSITION_TEX),
                    loaded -> {
                        diagnosticVolumeShaders.put(program, loaded);
                        logProgramBinarySize(program.serializedName(), loaded);
                    });
        } catch (IOException | RuntimeException failure) {
            ProjectAtmosphere.LOGGER.error(
                    "[VolumetricClouds] diagnostic program {} failed to load",
                    id, failure);
        }
    }

    /**
     * T204. The linked program's binary length (ARB_get_program_binary), the
     * one compiler characteristic the driver exposes without vendor tooling:
     * on NVIDIA the binary carries the assembled machine code, so its size
     * tracks the instruction count. Logged once per program at link time,
     * so a campaign's arms can be compared for code presence.
     */
    private static void logProgramBinarySize(String name, ShaderInstance shader) {
        try {
            if (!org.lwjgl.opengl.GL.getCapabilities().GL_ARB_get_program_binary) {
                return;
            }
            int bytes = org.lwjgl.opengl.GL41.glGetProgrami(shader.getId(),
                    org.lwjgl.opengl.GL41.GL_PROGRAM_BINARY_LENGTH);
            ProjectAtmosphere.LOGGER.info("T204_PROGRAM_BINARY program={} bytes={}", name, bytes);
            if ("lean_final".equals(name)) {
                // T207. The phase's aux attachment is fragment output 1; the
                // linker assigns the locations, so the assignment is evidence
                // rather than an assumption.
                ProjectAtmosphere.LOGGER.info("T207_FRAG_DATA program={} fragColor={} fragTemporalAux={}",
                        name,
                        org.lwjgl.opengl.GL30.glGetFragDataLocation(shader.getId(), "fragColor"),
                        org.lwjgl.opengl.GL30.glGetFragDataLocation(shader.getId(), "fragTemporalAux"));
            }
        } catch (RuntimeException failure) {
            ProjectAtmosphere.LOGGER.debug("program binary length unavailable for {}", name, failure);
        }
    }

    /**
     * True when every program a FINAL frame draws with is available: the lean
     * program and, since T206, the gate build and the rain pass.
     */
    public static boolean leanFinalShaderReady() {
        return leanFinalVolumeShader != null
                && diagnosticVolumeShaders.get(CoreCostDiagnosticProgram.RAIN_PASS) != null
                && diagnosticVolumeShaders.get(CoreCostDiagnosticProgram.RAIN_GATE_BUILD) != null;
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

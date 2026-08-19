package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateHolder;
import net.Gabou.projectatmosphere.clouds.client.debug.field.CloudFieldDebugRenderConfig;
import net.Gabou.projectatmosphere.clouds.client.render.ClientCloudRenderOwnership;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderDebugMode;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderStateGuard;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeDebugMode;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderConfig;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderFilter;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderMode;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeTunePreset;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeTuneTarget;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugRenderHook;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugStateInitializer;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.CloudWeatherMapRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.PuffLobeSpatialIndex;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormGeometryBuildCoordinator;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudFrameDiagnostics;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudDebugConfig;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderHook;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRaymarchDebugView;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricPuffShapeMode;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricPuffDensityStage;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricPuffTierFilter;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.telemetry.TelemetryExportService;
import net.Gabou.projectatmosphere.tools.debug.WorldSpaceDebugCubeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TelemetryDebugClientCommand {

    @SubscribeEvent
    public static void onRegisterClientCommand(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("pa")
                        .then(buildClientCloudFieldsCommand())
                        .then(Commands.literal("system")
                                .then(Commands.literal("telemetry")
                                        .then(Commands.literal("open")
                                                .executes(ctx -> {
                                                    if (!AtmoCommonConfig.TELEMETRY_ENABLED.get()) {
                                                        ctx.getSource().sendFailure(Component.literal("Telemetry export is disabled in the config."));
                                                        return 0;
                                                    }
                                                    TelemetryExportService.get().openTelemetryFolder(ctx.getSource());
                                                    return 1;
                                                })))
                                .then(Commands.literal("cloudDebug")
                                        .executes(ctx -> {
                                            if (FMLEnvironment.production) {
                                                ctx.getSource().sendFailure(Component.literal("This command is only available in a development environment."));
                                                return 0;
                                            } else {
                                                CloudDebugRenderHook.changeCloudDebugRenderHook();
                                                if (CloudDebugRenderHook.isCloudDebugRenderEnabled() && Minecraft.getInstance().player != null) {
                                                    CloudDebugStateInitializer.initialize(Minecraft.getInstance().player.position());
                                                }
                                                ctx.getSource().sendSuccess(() -> Component.literal("Toggled cloud debug render."), false);
                                                return 1;
                                            }
                                        }))
                                .then(Commands.literal("cloudStatus")
                                        .executes(ctx -> {
                                            int cachedRegions = ClientCloudRegionDataCache.getCurrentRegions().size();
                                            int cachedFields = ClientCloudFieldCache.getCurrentSnapshots().size();
                                            boolean hasDebugSnapshot = CloudRenderStateHolder.getInstance().hasDebugSnapshot();
                                            ClientCloudRenderOwnership.Owner owner = ClientCloudRenderOwnership.resolve(
                                                    Minecraft.getInstance().level);

                                            StringBuilder message = new StringBuilder("Cloud client status")
                                                    .append("\ncacheRegions=").append(cachedRegions)
                                                    .append("\ncacheCloudFields=").append(cachedFields)
                                                    .append("\ndebugSnapshot=").append(hasDebugSnapshot)
                                                    .append("\ncloudOwner=").append(owner)
                                                    .append("\nvolumetric=").append(VolumetricCloudRenderHook.status());

                                            if (cachedRegions > 0) {
                                                CloudRegionRenderData data = ClientCloudRegionDataCache.getCurrentRegions().get(0);
                                                message.append("\nfirstRegion=")
                                                        .append(data.getRegionId())
                                                        .append(" dim=")
                                                        .append(data.getDimensionId())
                                                        .append(" active=")
                                                        .append(data.isActive())
                                                        .append(" type=")
                                                        .append(data.getCloudTypeId())
                                                        .append(" profileDensity=")
                                                        .append(String.format(java.util.Locale.ROOT, "%.2f", data.getDensityMultiplier()))
                                                        .append(" baseTop=")
                                                        .append(String.format(java.util.Locale.ROOT, "%.1f/%.1f", data.getBaseY(), data.getTopY()))
                                                        .append(" center=")
                                                        .append(String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", data.getCenter().x(), data.getCenter().y(), data.getCenter().z()));
                                            }

                                            ctx.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
                                            return 1;
                                        }))
                                .then(buildVolumetricCloudCommand())
                                .then(buildWorldSpaceTestCubeCommand())
                                .then(buildCloudRenderDebugCommand())
                                .then(buildCloudFieldDebugCommand())
                                .then(buildCloudFieldVolumeControls("cloudFieldVolume"))
                                .then(TornadoRenderDebugClientCommand.build())
                        )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildVolumetricCloudCommand() {
        return Commands.literal("volumetric")
                .executes(ctx -> sendVolumetricStatus(ctx.getSource()))
                .then(Commands.literal("status")
                        .executes(ctx -> sendVolumetricStatus(ctx.getSource())))
                .then(Commands.literal("diagnostics")
                        .executes(ctx -> sendVolumetricDiagnostics(ctx.getSource()))
                        .then(Commands.literal("cumulus")
                                .executes(ctx -> requestVolumetricCumulusDiagnostics(ctx.getSource())))
                        .then(Commands.literal("stability")
                                .executes(ctx -> requestVolumetricStabilityDiagnostics(ctx.getSource(), 8))
                                .then(Commands.argument("frames", IntegerArgumentType.integer(2, 32))
                                        .executes(ctx -> requestVolumetricStabilityDiagnostics(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "frames")
                                        ))))
                        .then(Commands.literal("puffIndex")
                                .executes(ctx -> verifyVolumetricPuffIndex(ctx.getSource())))
                        .then(Commands.literal("renderState")
                                .executes(ctx -> verifyVolumetricRenderState(ctx.getSource())))
                        .then(Commands.literal("stormDensity")
                                .executes(ctx -> reportStormDensityCalibration(ctx.getSource())))
                        .then(Commands.literal("log")
                                .executes(ctx -> sendVolumetricDiagnosticsLogStatus(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> setVolumetricDiagnosticsLog(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setVolumetricDiagnosticsLog(ctx.getSource(), false)))))
                .then(Commands.literal("debug")
                        .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                        .then(Commands.literal("depthComposite")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> setVolumetricDepthComposite(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setVolumetricDepthComposite(ctx.getSource(), false))))
                        .then(Commands.literal("sceneRayLimit")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> setVolumetricSceneRayLimit(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setVolumetricSceneRayLimit(ctx.getSource(), false))))
                        .then(Commands.literal("coveragePretest")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> setVolumetricCoveragePretest(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setVolumetricCoveragePretest(ctx.getSource(), false))))
                        .then(Commands.literal("adaptiveWeatherFootprint")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> setVolumetricAdaptiveWeatherFootprint(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setVolumetricAdaptiveWeatherFootprint(ctx.getSource(), false))))
                        .then(Commands.literal("structuredPuff")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> setVolumetricStructuredPuff(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setVolumetricStructuredPuff(ctx.getSource(), false))))
                         .then(Commands.literal("directPuff")
                                 .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                 .then(Commands.literal("on")
                                         .executes(ctx -> setVolumetricPuffShapeMode(
                                                 ctx.getSource(), VolumetricPuffShapeMode.HYBRID)))
                                 .then(Commands.literal("off")
                                         .executes(ctx -> setVolumetricPuffShapeMode(
                                                 ctx.getSource(), VolumetricPuffShapeMode.FALLBACK_ONLY)))
                                 .then(Commands.literal("only")
                                         .executes(ctx -> setVolumetricPuffShapeMode(
                                                 ctx.getSource(), VolumetricPuffShapeMode.DIRECT_ONLY))))
                        .then(buildVolumetricPuffDensityCommand())
                        .then(buildVolumetricPuffTierFilterCommand())
                         .then(Commands.literal("history")
                                 .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                 .then(Commands.literal("on")
                                         .executes(ctx -> setVolumetricHistory(ctx.getSource(), true)))
                                 .then(Commands.literal("off")
                                         .executes(ctx -> setVolumetricHistory(ctx.getSource(), false))))
                        .then(Commands.literal("governor")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> resetVolumetricGovernor(ctx.getSource()))))
                         .then(buildVolumetricRaymarchViewCommand())
                        .then(Commands.literal("sentinelHeights")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> setVolumetricSentinelHeights(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setVolumetricSentinelHeights(ctx.getSource(), false))))
                        .then(Commands.literal("coveragePretestSamples")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.argument("value", IntegerArgumentType.integer(6, 16))
                                        .executes(ctx -> setVolumetricCoveragePretestSamples(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "value")
                                        ))))
                        .then(Commands.literal("coveragePretestThreshold")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 0.05F))
                                        .executes(ctx -> setVolumetricCoveragePretestThreshold(
                                                ctx.getSource(),
                                                FloatArgumentType.getFloat(ctx, "value")
                                        ))))
                        .then(Commands.literal("coveragePretestDilation")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 2))
                                        .executes(ctx -> setVolumetricCoveragePretestDilation(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "value")
                                        ))))
                        .then(Commands.literal("weatherCoverageScale")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.25F, 4.0F))
                                        .executes(ctx -> setVolumetricWeatherCoverageScale(
                                                ctx.getSource(),
                                                FloatArgumentType.getFloat(ctx, "value")
                                        ))))
                        .then(Commands.literal("fullres")
                                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> setVolumetricFullResolution(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setVolumetricFullResolution(ctx.getSource(), false)))))
                .then(Commands.literal("on")
                        .executes(ctx -> setVolumetricRuntimeEnabled(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(ctx -> setVolumetricRuntimeEnabled(ctx.getSource(), false)));
    }

    private static int sendVolumetricStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("Volumetric clouds\n" + VolumetricCloudRenderHook.status().replace(" ", "\n")),
                false);
        return 1;
    }

    private static int sendVolumetricDiagnostics(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(VolumetricCloudFrameDiagnostics.formattedLatest()),
                false);
        return 1;
    }

    private static int requestVolumetricCumulusDiagnostics(CommandSourceStack source) {
        String result = VolumetricCloudFrameDiagnostics.requestCumulusStageCapture();
        source.sendSuccess(
                () -> Component.literal("Cumulus stage-map capture " + result
                        + ". The transfer is fence-gated; results are written to latest.log "
                        + "and exposed by /pa system volumetric diagnostics."),
                false
        );
        return result.startsWith("requested") ? 1 : 0;
    }

    private static int requestVolumetricStabilityDiagnostics(CommandSourceStack source, int frames) {
        String result = VolumetricCloudFrameDiagnostics.requestStabilityCapture(frames);
        source.sendSuccess(
                () -> Component.literal("Volumetric stability capture " + result
                        + ". Existing raymarch/composite modes are measured through a fence-gated PBO; "
                        + "the numerical report is written to latest.log and exposed by diagnostics."),
                false
        );
        return result.startsWith("requested") ? 1 : 0;
    }

    private static int verifyVolumetricPuffIndex(CommandSourceStack source) {
        String result = PuffLobeSpatialIndex.verifyCurrentRepresentation();
        source.sendSuccess(
                () -> Component.literal("PUFF index verification written to latest.log\n" + result),
                false
        );
        return result.startsWith("unavailable") ? 0 : 1;
    }

    /**
     * T098 calibration scaffolding. Reports the live {@code cell.density()}
     * values reaching the descriptor coverage envelope, nearest storm first,
     * both in chat and in latest.log so the values can be copied out.
     */
    private static int reportStormDensityCalibration(CommandSourceStack source) {
        Vec3 position = source.getPosition();
        String report = StormGeometryBuildCoordinator.describeDensityCalibration(
                position.x, position.y, position.z
        );
        ProjectAtmosphere.LOGGER.info("[T098] {}", report);
        source.sendSuccess(() -> Component.literal(report), false);
        return report.contains("no descriptor-owned storm") ? 0 : 1;
    }

    private static int verifyVolumetricRenderState(CommandSourceStack source) {
        String result = CloudRenderStateGuard.verifyTextureStateRoundTrip();
        ProjectAtmosphere.LOGGER.info("[VolumetricClouds] render-state probe {}", result);
        source.sendSuccess(
                () -> Component.literal("Volumetric render-state probe " + result),
                false
        );
        return result.startsWith("passed") ? 1 : 0;
    }

    private static int sendVolumetricDebugStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug\n" + VolumetricCloudDebugConfig.status()),
                false);
        return 1;
    }

    private static int setVolumetricDepthComposite(CommandSourceStack source, boolean enabled) {
        VolumetricCloudDebugConfig.setDepthCompositeEnabled(enabled);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug depthComposite " + (enabled ? "on" : "off")),
                false);
        return 1;
    }

    private static int setVolumetricSceneRayLimit(CommandSourceStack source, boolean enabled) {
        VolumetricCloudDebugConfig.setSceneRayLimitEnabled(enabled);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug sceneRayLimit " + (enabled ? "on" : "off")),
                false);
        return 1;
    }

    private static int setVolumetricFullResolution(CommandSourceStack source, boolean enabled) {
        VolumetricCloudDebugConfig.setFullResolutionEnabled(enabled);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug fullres " + (enabled ? "on" : "off")),
                false);
        return 1;
    }

    private static int setVolumetricCoveragePretest(CommandSourceStack source, boolean enabled) {
        VolumetricCloudDebugConfig.setCoveragePretestEnabled(enabled);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug coveragePretest " + (enabled ? "on" : "off")),
                false);
        return 1;
    }

    private static int setVolumetricAdaptiveWeatherFootprint(CommandSourceStack source, boolean enabled) {
        VolumetricCloudDebugConfig.setAdaptiveWeatherFootprintEnabled(enabled);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug adaptiveWeatherFootprint "
                        + (enabled ? "on" : "off")),
                false);
        return 1;
    }

    private static int setVolumetricHistory(CommandSourceStack source, boolean enabled) {
        VolumetricCloudDebugConfig.setHistoryEnabled(enabled);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug history " + (enabled ? "on" : "off")),
                false);
        return 1;
    }

    private static int setVolumetricStructuredPuff(CommandSourceStack source, boolean enabled) {
        VolumetricCloudDebugConfig.setStructuredPuffEnabled(enabled);
        CloudWeatherMapRenderer.invalidateCache();
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug structuredPuff "
                        + (enabled ? "on" : "off")),
                false);
        return 1;
    }

    private static int setVolumetricPuffShapeMode(
            CommandSourceStack source,
            VolumetricPuffShapeMode mode
    ) {
        VolumetricCloudDebugConfig.setPuffShapeMode(mode);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug puffShape " + mode.serializedName()),
                false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildVolumetricPuffDensityCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("puffDensity")
                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()));
        for (VolumetricPuffDensityStage stage : VolumetricPuffDensityStage.values()) {
            root.then(Commands.literal(stage.serializedName())
                    .executes(ctx -> setVolumetricPuffDensityStage(ctx.getSource(), stage)));
        }
        return root;
    }

    private static int setVolumetricPuffDensityStage(
            CommandSourceStack source,
            VolumetricPuffDensityStage stage
    ) {
        if (stage.isDiagnostic() && !PuffLobeSpatialIndex.directRepresentationComplete()) {
            source.sendFailure(Component.literal(
                    "Cannot select puffDensity " + stage.serializedName()
                            + ": direct PUFF representation is incomplete. "
                            + PuffLobeSpatialIndex.status()
            ));
            return 0;
        }
        // Diagnostic stages are interpreted as direct-only by the central
        // effective-mode contract. Do not mutate the user's requested shape
        // mode: returning to FINAL must restore it automatically.
        VolumetricCloudDebugConfig.setPuffDensityStage(stage);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal(
                        "Volumetric cloud debug puffDensity " + stage.serializedName()
                                + (stage.isDiagnostic() ? " (direct-only, complete)" : "")
                ),
                false
        );
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildVolumetricPuffTierFilterCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("puffTier")
                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()));
        for (VolumetricPuffTierFilter filter : VolumetricPuffTierFilter.values()) {
            root.then(Commands.literal(filter.serializedName())
                    .executes(ctx -> setVolumetricPuffTierFilter(ctx.getSource(), filter)));
        }
        return root;
    }

    private static int setVolumetricPuffTierFilter(
            CommandSourceStack source,
            VolumetricPuffTierFilter filter
    ) {
        VolumetricCloudDebugConfig.setPuffTierFilter(filter);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug puffTier " + filter.serializedName()),
                false
        );
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildVolumetricRaymarchViewCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("view")
                .executes(ctx -> sendVolumetricDebugStatus(ctx.getSource()));
        for (VolumetricCloudRaymarchDebugView view : VolumetricCloudRaymarchDebugView.values()) {
            root.then(Commands.literal(view.serializedName())
                    .executes(ctx -> setVolumetricRaymarchView(ctx.getSource(), view)));
        }
        return root;
    }

    private static int setVolumetricRaymarchView(
            CommandSourceStack source,
            VolumetricCloudRaymarchDebugView view
    ) {
        VolumetricCloudRaymarchDebugView previous = VolumetricCloudDebugConfig.raymarchDebugView();
        VolumetricCloudDebugConfig.setRaymarchDebugView(view);
        // Diagnostic views overwrite the current target but deliberately keep
        // the last production target frozen. Invalidate only when returning to
        // production so a stale diagnostic interval can never be blended.
        if (view == VolumetricCloudRaymarchDebugView.FINAL
                && previous != VolumetricCloudRaymarchDebugView.FINAL) {
            VolumetricCloudRenderer.invalidateHistory();
        }
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud raymarch view " + view.serializedName()),
                false
        );
        return 1;
    }

    private static int setVolumetricSentinelHeights(CommandSourceStack source, boolean enabled) {
        VolumetricCloudDebugConfig.setSentinelHeightsEnabled(enabled);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug sentinelHeights " + (enabled ? "on" : "off")),
                false);
        return 1;
    }

    private static int setVolumetricCoveragePretestSamples(CommandSourceStack source, int samples) {
        VolumetricCloudDebugConfig.setCoveragePretestSamples(samples);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug coveragePretestSamples "
                        + VolumetricCloudDebugConfig.coveragePretestSamples()),
                false);
        return 1;
    }

    private static int setVolumetricCoveragePretestThreshold(CommandSourceStack source, float threshold) {
        VolumetricCloudDebugConfig.setCoveragePretestThreshold(threshold);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug coveragePretestThreshold "
                        + VolumetricCloudDebugConfig.coveragePretestThreshold()),
                false);
        return 1;
    }

    private static int setVolumetricCoveragePretestDilation(CommandSourceStack source, int dilation) {
        VolumetricCloudDebugConfig.setCoveragePretestDilation(dilation);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug coveragePretestDilation "
                        + VolumetricCloudDebugConfig.coveragePretestDilation()),
                false);
        return 1;
    }

    private static int setVolumetricWeatherCoverageScale(CommandSourceStack source, float scale) {
        VolumetricCloudDebugConfig.setWeatherCoverageScale(scale);
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud debug weatherCoverageScale "
                        + VolumetricCloudDebugConfig.weatherCoverageScale()),
                false);
        return 1;
    }

    private static int sendVolumetricDiagnosticsLogStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud diagnostics per-frame log="
                        + (VolumetricCloudFrameDiagnostics.isFrameLogEnabled() ? "on" : "off")
                        + "\ncommand=/pa cloud volumetric diagnostics log <on|off>"),
                false);
        return 1;
    }

    private static int setVolumetricDiagnosticsLog(CommandSourceStack source, boolean enabled) {
        if (enabled && !ProjectAtmosphere.DEBUG_MODE) {
            source.sendFailure(Component.literal("Volumetric diagnostics per-frame logging requires Project Atmosphere debug mode."));
            return 0;
        }
        VolumetricCloudFrameDiagnostics.setFrameLogEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud diagnostics per-frame log "
                        + (enabled ? "enabled" : "disabled") + "."),
                false);
        return 1;
    }

    private static int setVolumetricRuntimeEnabled(CommandSourceStack source, boolean enabled) {
        VolumetricCloudRenderHook.setRuntimeEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud pipeline runtime switch: " + (enabled ? "on" : "off")
                        + (enabled ? "" : " (legacy CloudField volume renderer takes over next frame)")),
                false);
        return 1;
    }

    private static int resetVolumetricGovernor(CommandSourceStack source) {
        VolumetricCloudRenderer.resetGovernor();
        VolumetricCloudRenderer.invalidateHistory();
        source.sendSuccess(
                () -> Component.literal("Volumetric cloud governor reset; scale="
                        + VolumetricCloudRenderer.governorStepScale()),
                false
        );
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildWorldSpaceTestCubeCommand() {
        return Commands.literal("worldSpaceTestCube")
                .executes(ctx -> sendWorldSpaceTestCubeStatus(ctx.getSource()))
                .then(Commands.literal("on")
                        .executes(ctx -> setWorldSpaceTestCube(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(ctx -> setWorldSpaceTestCube(ctx.getSource(), false)))
                .then(Commands.literal("status")
                        .executes(ctx -> sendWorldSpaceTestCubeStatus(ctx.getSource())));
    }

    private static int setWorldSpaceTestCube(CommandSourceStack source, boolean enabled) {
        if (FMLEnvironment.production) {
            source.sendFailure(Component.literal("This command is only available in a development environment."));
            return 0;
        }

        WorldSpaceDebugCubeRenderer.setEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal(enabled
                        ? "World-space test cube enabled. Anchor captures from player position on the next render frame."
                        : "World-space test cube disabled."),
                false
        );
        return 1;
    }

    private static int sendWorldSpaceTestCubeStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(WorldSpaceDebugCubeRenderer.status()),
                false
        );
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCloudRenderDebugCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cloudRenderDebug")
                .executes(ctx -> sendCloudRenderDebugStatus(ctx.getSource()));

        for (CloudRenderDebugMode mode : CloudRenderDebugMode.values()) {
            root.then(Commands.literal(mode.serializedName())
                    .executes(ctx -> setCloudRenderDebugMode(ctx.getSource(), mode)));
        }

        root.then(Commands.literal("status")
                .executes(ctx -> sendCloudRenderDebugStatus(ctx.getSource())));
        return root;
    }

    private static int setCloudRenderDebugMode(CommandSourceStack source, CloudRenderDebugMode mode) {
        CloudRenderDebugMode.setCurrent(mode);
        source.sendSuccess(
                () -> Component.literal("Cloud render debug mode set to " + mode.serializedName() + " (" + mode.id() + ")."),
                false
        );
        return 1;
    }

    private static int sendCloudRenderDebugStatus(CommandSourceStack source) {
        CloudRenderDebugMode mode = CloudRenderDebugMode.current();
        source.sendSuccess(
                () -> Component.literal("Cloud render debug mode is " + mode.serializedName() + " (" + mode.id() + ")."),
                false
        );
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCloudFieldDebugCommand() {
        return buildCloudFieldLegacyDebugControls("cloudFieldDebug");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCloudFieldLegacyDebugControls(String literalName) {
        return Commands.literal(literalName)
                .executes(ctx -> sendCloudFieldDebugStatus(ctx.getSource()))
                .then(Commands.literal("on")
                        .executes(ctx -> setCloudFieldDebug(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(ctx -> setCloudFieldDebug(ctx.getSource(), false)))
                .then(Commands.literal("status")
                        .executes(ctx -> sendCloudFieldDebugStatus(ctx.getSource())));
    }

    private static int setCloudFieldDebug(CommandSourceStack source, boolean enabled) {
        if (enabled) {
            CloudFieldVolumeRenderConfig.setEnabled(false);
        }
        CloudFieldDebugRenderConfig.setEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal(enabled
                        ? "Legacy CloudField debug renderer enabled. CloudField volume renderer disabled to avoid duplicate rendering."
                        : "Legacy CloudField debug renderer disabled."),
                false
        );
        return 1;
    }

    private static int sendCloudFieldDebugStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(CloudFieldDebugRenderConfig.status()
                        + "\ncachedCloudFields=" + ClientCloudFieldCache.getCurrentSnapshots().size()
                        + "\n" + cloudFieldRendererOwnershipStatus()),
                false
        );
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildClientCloudFieldsCommand() {
        return Commands.literal("cloud")
                .then(buildCloudFieldVolumeControls("render"))
                .then(buildVolumetricCloudCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCloudFieldVolumeControls(String literalName) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(literalName)
                .executes(ctx -> sendCloudFieldVolumeStatus(ctx.getSource()))
                .then(Commands.literal("on")
                        .executes(ctx -> setCloudFieldVolumeRender(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(ctx -> setCloudFieldVolumeRender(ctx.getSource(), false)))
                .then(Commands.literal("status")
                        .executes(ctx -> sendCloudFieldVolumeStatus(ctx.getSource()))
                        .then(Commands.literal("verbose")
                                .executes(ctx -> sendCloudFieldVolumeVerboseStatus(ctx.getSource()))));

        LiteralArgumentBuilder<CommandSourceStack> modeRoot = Commands.literal("mode");
        for (CloudFieldVolumeRenderMode mode : CloudFieldVolumeRenderMode.values()) {
            modeRoot.then(Commands.literal(mode.serializedName())
                    .executes(ctx -> setCloudFieldVolumeMode(ctx.getSource(), mode)));
        }
        root.then(modeRoot);

        LiteralArgumentBuilder<CommandSourceStack> filterRoot = Commands.literal("filter");
        for (CloudFieldVolumeRenderFilter filter : CloudFieldVolumeRenderFilter.values()) {
            filterRoot.then(Commands.literal(filter.serializedName())
                    .executes(ctx -> setCloudFieldVolumeFilter(ctx.getSource(), filter)));
        }
        root.then(filterRoot);

        LiteralArgumentBuilder<CommandSourceStack> compositeRoot = Commands.literal("composite")
                .executes(ctx -> sendCloudFieldCompositeModeStatus(ctx.getSource()));
        for (CloudFieldCompositeDebugMode compositeMode : CloudFieldCompositeDebugMode.values()) {
            compositeRoot.then(Commands.literal(compositeMode.serializedName())
                    .executes(ctx -> setCloudFieldCompositeMode(ctx.getSource(), compositeMode)));
        }
        root.then(compositeRoot);
        root.then(buildCloudFieldVolumeQualityCommand());
        root.then(buildCloudFieldVolumeTuneCommand());
        return root;
    }

    private static int setCloudFieldVolumeRender(CommandSourceStack source, boolean enabled) {
        if (enabled) {
            CloudFieldDebugRenderConfig.setEnabled(false);
        } else {
            VolumetricCloudRenderHook.setRuntimeEnabled(false);
            CloudFieldDebugRenderConfig.setEnabled(false);
        }
        CloudFieldVolumeRenderConfig.setEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal(enabled
                        ? "CloudField volume renderer enabled. Legacy CloudField debug renderer disabled."
                        : "CloudField volume renderer disabled."),
                false
        );
        return 1;
    }

    private static int setCloudFieldVolumeMode(CommandSourceStack source, CloudFieldVolumeRenderMode mode) {
        CloudFieldVolumeRenderConfig.setMode(mode);
        source.sendSuccess(
                () -> Component.literal("CloudField volume render mode set to "
                        + mode.serializedName()
                        + " ("
                        + mode.shaderId()
                        + ")."),
                false
        );
        return 1;
    }

    private static int setCloudFieldVolumeFilter(CommandSourceStack source, CloudFieldVolumeRenderFilter filter) {
        CloudFieldVolumeRenderConfig.setFilter(filter);
        source.sendSuccess(
                () -> Component.literal("CloudField volume render filter set to "
                        + filter.serializedName()
                        + "."),
                false
        );
        return 1;
    }

    private static int setCloudFieldCompositeMode(CommandSourceStack source, CloudFieldCompositeDebugMode mode) {
        CloudFieldVolumeRenderConfig.setCompositeDebugMode(mode);
        source.sendSuccess(
                () -> Component.literal("CloudField composite mode set to " + mode.serializedName() + "."),
                false
        );
        return 1;
    }

    private static int sendCloudFieldCompositeModeStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("CloudField composite mode="
                        + CloudFieldVolumeRenderConfig.compositeDebugMode().serializedName()
                        + "\ncommand=/pa cloud render composite "
                        + "<final|color|depth|alignment|alpha|spatial|selected_neighbor|scene_rejection>"),
                false
        );
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCloudFieldVolumeQualityCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("quality")
                .executes(ctx -> sendCloudFieldVolumeQualityStatus(ctx.getSource()))
                .then(Commands.literal("status")
                        .executes(ctx -> sendCloudFieldVolumeQualityStatus(ctx.getSource())));

        for (AtmoCommonConfig.CloudRaymarchQuality quality : AtmoCommonConfig.CloudRaymarchQuality.values()) {
            root.then(Commands.literal(CloudFieldVolumeRenderConfig.serializedQualityName(quality))
                    .executes(ctx -> setCloudFieldVolumeQuality(ctx.getSource(), quality)));
        }
        root.then(Commands.literal("low_24_steps")
                .executes(ctx -> setCloudFieldVolumeQuality(ctx.getSource(), AtmoCommonConfig.CloudRaymarchQuality.LOW_24)));
        root.then(Commands.literal("sublime")
                .executes(ctx -> setCloudFieldVolumeQuality(ctx.getSource(), AtmoCommonConfig.CloudRaymarchQuality.ULTRA)));
        return root;
    }

    private static int setCloudFieldVolumeQuality(CommandSourceStack source, AtmoCommonConfig.CloudRaymarchQuality quality) {
        CloudFieldVolumeRenderConfig.setQuality(quality);
        source.sendSuccess(
                () -> Component.literal("CloudField render quality set to "
                        + CloudFieldVolumeRenderConfig.serializedQualityName(quality)
                        + ".\n"
                        + CloudFieldVolumeRenderConfig.qualityStatus()),
                false
        );
        return 1;
    }

    private static int sendCloudFieldVolumeQualityStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("CloudField render quality\n"
                        + CloudFieldVolumeRenderConfig.qualityStatus()
                        + "\ncommand=/pa cloud render quality <low|low_24|low_24_steps|medium|high|ultra>"),
                false
        );
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCloudFieldVolumeTuneCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tune")
                .executes(ctx -> sendCloudFieldVolumeTuneStatus(ctx.getSource()))
                .then(Commands.literal("status")
                        .executes(ctx -> sendCloudFieldVolumeTuneStatus(ctx.getSource())))
                .then(Commands.literal("reset")
                        .executes(ctx -> resetCloudFieldVolumeTuning(ctx.getSource())));

        for (CloudFieldVolumeTuneTarget target : CloudFieldVolumeTuneTarget.values()) {
            root.then(Commands.literal(target.serializedName())
                    .then(Commands.argument("value", FloatArgumentType.floatArg(target.min(), target.max()))
                            .executes(ctx -> setCloudFieldVolumeTuning(
                                    ctx.getSource(),
                                    target,
                                    FloatArgumentType.getFloat(ctx, "value")
                            ))));
        }

        LiteralArgumentBuilder<CommandSourceStack> presetRoot = Commands.literal("preset");
        for (CloudFieldVolumeTunePreset preset : CloudFieldVolumeTunePreset.values()) {
            presetRoot.then(Commands.literal(preset.serializedName())
                    .executes(ctx -> setCloudFieldVolumeTunePreset(ctx.getSource(), preset)));
        }
        root.then(presetRoot);
        return root;
    }

    private static int setCloudFieldVolumeTuning(
            CommandSourceStack source,
            CloudFieldVolumeTuneTarget target,
            float value
    ) {
        CloudFieldVolumeRenderConfig.setTuning(target, value);
        source.sendSuccess(
                () -> Component.literal("CloudField volume tune "
                        + target.serializedName()
                        + "="
                        + CloudFieldVolumeRenderConfig.tuningValue(target)
                        + "\n"
                        + CloudFieldVolumeRenderConfig.tuningStatus()),
                false
        );
        return 1;
    }

    private static int setCloudFieldVolumeTunePreset(CommandSourceStack source, CloudFieldVolumeTunePreset preset) {
        CloudFieldVolumeRenderConfig.applyPreset(preset);
        source.sendSuccess(
                () -> Component.literal("CloudField volume tune preset "
                        + preset.serializedName()
                        + " applied.\n"
                        + CloudFieldVolumeRenderConfig.tuningStatus()),
                false
        );
        return 1;
    }

    private static int resetCloudFieldVolumeTuning(CommandSourceStack source) {
        CloudFieldVolumeRenderConfig.resetTuning();
        source.sendSuccess(
                () -> Component.literal("CloudField volume tuning reset.\n"
                        + CloudFieldVolumeRenderConfig.tuningStatus()),
                false
        );
        return 1;
    }

    private static int sendCloudFieldVolumeTuneStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("CloudField volume tuning\n"
                        + CloudFieldVolumeRenderConfig.tuningStatus()
                        + "\ncommands=/pa cloud render tune <opacity|threshold|erosion|noise|brightness|underside|maxalpha|densityboost|animspeed> <value>"
                        + "\npreset=/pa cloud render tune preset <soft|dense|wispy|debug>"
                        + "\nreset=/pa cloud render tune reset"),
                false
        );
        return 1;
    }

    private static int sendCloudFieldVolumeStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(CloudFieldVolumeRenderConfig.status()
                        + "\ncachedCloudFields=" + ClientCloudFieldCache.getCurrentSnapshots().size()
                        + "\n" + cloudFieldRendererOwnershipStatus()),
                false
        );
        return 1;
    }

    private static int sendCloudFieldVolumeVerboseStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(CloudFieldVolumeRenderConfig.verboseStatus()
                        + "\ncachedCloudFields=" + ClientCloudFieldCache.getCurrentSnapshots().size()
                        + "\n" + cloudFieldRendererOwnershipStatus()
                        + "\ncommands=/pa cloud render on|off|status"
                        + "\ncompact=/pa cloud render status"
                        + "\nmode=/pa cloud render mode <normal|bounds|horizontal|height|vertical|density|source|densitymask>"
                        + "\nfilter=/pa cloud render filter <all|manual|weather|nearest|first>"
                        + "\ncomposite=/pa cloud render composite <final|color|depth|alignment|alpha>"
                        + "\nquality=/pa cloud render quality <low|low_24|low_24_steps|medium|high|ultra>"
                        + "\ntune=/pa cloud render tune <opacity|threshold|erosion|noise|brightness|underside|maxalpha|densityboost|animspeed> <value>"
                        + "\npreset=/pa cloud render tune preset <soft|dense|wispy|debug>"),
                false
        );
        return 1;
    }

    private static String cloudFieldRendererOwnershipStatus() {
        boolean volumeEnabled = CloudFieldVolumeRenderConfig.isEnabled();
        boolean legacyEnabled = CloudFieldDebugRenderConfig.shouldRender();
        return "volumeRendererEnabled=" + volumeEnabled
                + "\nlegacyCloudFieldRendererEnabled=" + legacyEnabled
                + "\nduplicateCloudFieldRenderPathsActive=" + (volumeEnabled && legacyEnabled)
                + "\nactiveCloudFieldRenderer=" + activeCloudFieldRenderer(volumeEnabled, legacyEnabled)
                + "\nactiveMainCloudRenderer=cloudfield_volume"
                + "\nlegacyCloudRendererEnabled=false"
                + "\nduplicateCloudRenderPathsActive=false";
    }

    private static String activeCloudFieldRenderer(boolean volumeEnabled, boolean legacyEnabled) {
        if (volumeEnabled && legacyEnabled) {
            return "conflict";
        }
        if (volumeEnabled) {
            return "volume";
        }
        if (legacyEnabled) {
            return "legacy";
        }
        return "none";
    }

}

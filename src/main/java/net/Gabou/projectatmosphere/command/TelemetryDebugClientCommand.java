package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderController;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateHolder;
import net.Gabou.projectatmosphere.clouds.client.debug.field.CloudFieldDebugRenderConfig;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderFallbackState;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderDebugMode;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderConfig;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderFilter;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderMode;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeTunePreset;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeTuneTarget;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugRenderHook;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugStateInitializer;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.telemetry.TelemetryExportService;
import net.Gabou.projectatmosphere.tools.debug.WorldSpaceDebugCubeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
                                            int currentSnapshots = CloudRenderStateHolder.getInstance().getCurrentSnapshots().size();
                                            int renderableSnapshots = CloudRenderController.getRenderableLiveSnapshots().size();
                                            boolean hasDebugSnapshot = CloudRenderStateHolder.getInstance().hasDebugSnapshot();
                                            CloudRenderFallbackState.FailureStatus fallbackStatus = CloudRenderFallbackState.getStatus();

                                            StringBuilder message = new StringBuilder("Cloud client status")
                                                    .append("\ncacheRegions=").append(cachedRegions)
                                                    .append("\ncacheCloudFields=").append(cachedFields)
                                                    .append("\ncurrentSnapshots=").append(currentSnapshots)
                                                    .append("\nrenderableLiveSnapshots=").append(renderableSnapshots)
                                                    .append("\ndebugSnapshot=").append(hasDebugSnapshot)
                                                    .append("\nfallbackActive=").append(fallbackStatus.active());

                                            if (fallbackStatus.active()) {
                                                message.append("\nfallbackTitle=").append(fallbackStatus.title())
                                                        .append("\nfallbackDetail=").append(fallbackStatus.detail());
                                            }

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

                                            if (currentSnapshots > 0) {
                                                CloudRenderSnapshot snapshot = CloudRenderStateHolder.getInstance().getCurrentSnapshots().get(0);
                                                message.append("\nfirstSnapshot=")
                                                        .append(snapshot.getDimension())
                                                        .append(" enabled=")
                                                        .append(snapshot.isEnabled())
                                                        .append(" radius=")
                                                        .append(String.format(java.util.Locale.ROOT, "%.1f", snapshot.getRegionRadius()))
                                                        .append(" age=")
                                                        .append(snapshot.getAgeTicks())
                                                        .append("/")
                                                        .append(snapshot.getLifetimeTicks());
                                                message.append(" type=")
                                                        .append(snapshot.getCloudTypeId())
                                                        .append(" tower=")
                                                        .append(String.format(java.util.Locale.ROOT, "%.2f", snapshot.getTowerStrength()))
                                                        .append(" anvil=")
                                                        .append(String.format(java.util.Locale.ROOT, "%.2f", snapshot.getAnvilStrength()))
                                                        .append(" vertical=")
                                                        .append(String.format(java.util.Locale.ROOT, "%.2f", snapshot.getVerticalThickness()))
                                                        .append(" squash=")
                                                        .append(String.format(java.util.Locale.ROOT, "%.2f", snapshot.getHeightSquash()));
                                            }

                                            ctx.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
                                            return 1;
                                        }))
                                .then(buildWorldSpaceTestCubeCommand())
                                .then(buildCloudRenderDebugCommand())
                                .then(buildCloudFieldDebugCommand())
                                .then(buildCloudFieldVolumeControls("cloudFieldVolume"))
                                .then(TornadoRenderDebugClientCommand.build())
                        )
        );
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
                .then(Commands.literal("fields")
                        .then(buildCloudFieldVolumeControls("render"))
                        .then(buildCloudFieldLegacyDebugControls("legacydebug")));
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
        root.then(buildCloudFieldVolumeTuneCommand());
        return root;
    }

    private static int setCloudFieldVolumeRender(CommandSourceStack source, boolean enabled) {
        if (enabled) {
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
                        + "\ncommands=/pa cloud fields render tune <opacity|threshold|erosion|noise|brightness|underside|maxalpha|densityboost|animspeed> <value>"
                        + "\npreset=/pa cloud fields render tune preset <soft|dense|wispy|debug>"
                        + "\nreset=/pa cloud fields render tune reset"),
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
                        + "\nlegacyRendererStatus="
                        + CloudFieldDebugRenderConfig.status().replace("\n", " | ")
                        + "\ncommands=/pa cloud fields render on|off|status"
                        + "\ncompact=/pa cloud fields render status"
                        + "\nlegacydebug=/pa cloud fields legacydebug on|off|status"
                        + "\nmode=/pa cloud fields render mode <normal|bounds|horizontal|height|vertical|density|source>"
                        + "\nfilter=/pa cloud fields render filter <all|manual|weather|nearest|first>"
                        + "\ntune=/pa cloud fields render tune <opacity|threshold|erosion|noise|brightness|underside|maxalpha|densityboost|animspeed> <value>"
                        + "\npreset=/pa cloud fields render tune preset <soft|dense|wispy|debug>"),
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
                + "\nactiveCloudFieldRenderer=" + activeCloudFieldRenderer(volumeEnabled, legacyEnabled);
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

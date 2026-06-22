package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
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
        return Commands.literal("cloudFieldDebug")
                .executes(ctx -> sendCloudFieldDebugStatus(ctx.getSource()))
                .then(Commands.literal("on")
                        .executes(ctx -> setCloudFieldDebug(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(ctx -> setCloudFieldDebug(ctx.getSource(), false)))
                .then(Commands.literal("status")
                        .executes(ctx -> sendCloudFieldDebugStatus(ctx.getSource())));
    }

    private static int setCloudFieldDebug(CommandSourceStack source, boolean enabled) {
        CloudFieldDebugRenderConfig.setEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal(enabled
                        ? "CloudField debug renderer enabled."
                        : "CloudField debug renderer disabled. It will still show while cloudRenderDebug is active."),
                false
        );
        return 1;
    }

    private static int sendCloudFieldDebugStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(CloudFieldDebugRenderConfig.status()
                        + "\ncachedCloudFields=" + ClientCloudFieldCache.getCurrentSnapshots().size()),
                false
        );
        return 1;
    }

}

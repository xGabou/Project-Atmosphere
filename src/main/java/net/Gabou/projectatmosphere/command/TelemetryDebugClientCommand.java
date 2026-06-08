package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderController;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateHolder;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugRenderHook;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugStateInitializer;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.telemetry.TelemetryExportService;
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
                        .then(Commands.literal("debug")
                                .then(TornadoRenderDebugClientCommand.build())
                                .then(Commands.literal("export")
                                        .executes(ctx -> {
                                            if (!AtmoCommonConfig.TELEMETRY_ENABLED.get()) {
                                                ctx.getSource().sendFailure(Component.literal("Telemetry export is disabled in the config."));
                                                return 0;
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal("Preparing telemetry archive..."), false);
                                            TelemetryExportService.get().exportAsync(ctx.getSource());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("open")
                                        .executes(ctx -> {
                                            if (!AtmoCommonConfig.TELEMETRY_ENABLED.get()) {
                                                ctx.getSource().sendFailure(Component.literal("Telemetry export is disabled in the config."));
                                                return 0;
                                            }
                                            TelemetryExportService.get().openTelemetryFolder(ctx.getSource());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("cloudDebug")
                                        .executes(ctx -> {
                                            if (FMLEnvironment.production) {
                                                ctx.getSource().sendFailure(Component.literal("This command is only available in a development environment."));
                                                return 0;
                                            }
                                            else {
                                                CloudDebugRenderHook.changeCloudDebugRenderHook();
                                                if (CloudDebugRenderHook.isCloudDebugRenderEnabled() && Minecraft.getInstance().player != null) {
                                                    CloudDebugStateInitializer.initialize(Minecraft.getInstance().player.position());
                                                }
                                                ctx.getSource().sendSuccess(() -> Component.literal("Toggled cloud debug render."), false);
                                                return 1;
                                            }

                                        })
                                )
                                .then(Commands.literal("cloudStatus")
                                        .executes(ctx -> {
                                            int cachedRegions = ClientCloudRegionDataCache.getCurrentRegions().size();
                                            int currentSnapshots = CloudRenderStateHolder.getInstance().getCurrentSnapshots().size();
                                            int renderableSnapshots = CloudRenderController.getRenderableLiveSnapshots().size();
                                            boolean hasDebugSnapshot = CloudRenderStateHolder.getInstance().hasDebugSnapshot();

                                            StringBuilder message = new StringBuilder("Cloud client status")
                                                    .append("\ncacheRegions=").append(cachedRegions)
                                                    .append("\ncurrentSnapshots=").append(currentSnapshots)
                                                    .append("\nrenderableLiveSnapshots=").append(renderableSnapshots)
                                                    .append("\ndebugSnapshot=").append(hasDebugSnapshot);

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
                                                        .append(String.format(java.util.Locale.ROOT, "%.2f", snapshot.getAnvilStrength()));
                                            }

                                            ctx.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
                                            return 1;
                                        })
                                )

                        )
        );
    }

}

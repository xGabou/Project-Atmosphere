package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.clouds.frontend.debug.CloudDebugRenderHook;
import net.Gabou.projectatmosphere.clouds.frontend.debug.CloudDebugStateInitializer;
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

                        )
        );
    }

}

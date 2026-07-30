package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.telemetry.TelemetryArchiveWriter;
import net.Gabou.projectatmosphere.telemetry.TelemetryCollector;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class CommandTelemetryService {
    private CommandTelemetryService() {
    }

    public static CompletableFuture<Path> exportTelemetry(CommandSourceStack source) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        AsyncAtmosphereService.runWeather(() -> {
            try {
                Path archive = exportNow(source);
                AsyncAtmosphereService.runOnMainThread(() -> source.sendSuccess(
                        () -> Component.literal("Server snapshot archive written to " + archive),
                        false
                ));
                future.complete(archive);
            } catch (Exception e) {
                ProjectAtmosphere.LOGGER.error("Server telemetry export failed", e);
                AsyncAtmosphereService.runOnMainThread(() -> source.sendFailure(
                        Component.literal("Telemetry export failed: " + e.getMessage())
                ));
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static Path exportNow(CommandSourceStack source) throws Exception {
        if (!AtmoCommonConfig.TELEMETRY_ENABLED.get()) {
            throw new IllegalStateException("Telemetry export disabled in config");
        }

        MinecraftServer server = source.getServer();
        if (server == null) {
            throw new IllegalStateException("Server export is only available from a server command");
        }

        Path telemetryDir = server.getWorldPath(LevelResource.ROOT)
                .resolve("project_atmosphere")
                .resolve("telemetry");
        return TelemetryArchiveWriter.exportSnapshot(telemetryDir, TelemetryCollector.get(), server);
    }
}

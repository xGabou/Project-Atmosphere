package net.Gabou.projectatmosphere.telemetry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.SessionHeader;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for writing telemetry snapshots to disk and packaging them into a zip archive.
 * Work is dispatched onto the CLIENT executor to avoid blocking the render thread.
 */
public final class TelemetryExportService {

    private static final TelemetryExportService INSTANCE = new TelemetryExportService();

    private TelemetryExportService() {
    }

    public static TelemetryExportService get() {
        return INSTANCE;
    }

    public CompletableFuture<Path> exportAsync(CommandSourceStack source) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        AsyncAtmosphereService.runClient(() -> {
            try {
                Path archive = exportNow();
                AsyncAtmosphereService.runOnMainThread(() -> notifySuccess(source, archive));
                future.complete(archive);
            } catch (Exception e) {
                ProjectAtmosphere.LOGGER.error("Telemetry export failed", e);
                AsyncAtmosphereService.runOnMainThread(() -> notifyFailure(source, e));
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void openTelemetryFolder(CommandSourceStack source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            notifyFailure(source, new IllegalStateException("Minecraft client not available"));
            return;
        }
        Path telemetryDir = getTelemetryDirectory(mc);
        try {
            Files.createDirectories(telemetryDir);
            Util.getPlatform().openUri(telemetryDir.toUri());
        } catch (Exception e) {
            notifyFailure(source, e);
        }
    }

    private Path exportNow() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            throw new IllegalStateException("Client export only");
        }
        if (!AtmoCommonConfig.TELEMETRY_ENABLED.get()) {
            throw new IllegalStateException("Telemetry export disabled in config");
        }

        Path telemetryDir = getTelemetryDirectory(mc);
        TelemetryCollector collector = TelemetryCollector.get();
        return TelemetryArchiveWriter.exportSnapshot(telemetryDir, collector);
    }

    private Path getTelemetryDirectory(Minecraft mc) {
        return mc.gameDirectory.toPath()
                .resolve("project_atmosphere")
                .resolve("telemetry");
    }

    private void notifySuccess(CommandSourceStack source, Path archive) {
        MutableComponent message = Component.literal("Telemetry archive ready: " + archive)
                .withStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, archive.toString())));
        sendToSource(source, message);
    }

    private void notifyFailure(CommandSourceStack source, Exception e) {
        MutableComponent message = Component.literal("Telemetry export failed: " + e.getMessage());
        sendToSource(source, message);
    }

    private void sendToSource(CommandSourceStack source, MutableComponent message) {
        if (source != null) {
            source.sendSuccess(() -> message, false);
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, false);
        }
    }
}

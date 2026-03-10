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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        Files.createDirectories(telemetryDir);

        TelemetryCollector collector = TelemetryCollector.get();
        String sessionId = collector.getHeader().sessionId;
        Path sessionDir = telemetryDir.resolve("session_" + sessionId);
        collector.writeSnapshot(sessionDir);

        Path archivePath = buildArchivePath(telemetryDir, collector.getHeader());
        zipDirectory(sessionDir, archivePath);
        pruneOldArchives(telemetryDir);
        return archivePath;
    }

    private Path getTelemetryDirectory(Minecraft mc) {
        return mc.gameDirectory.toPath()
                .resolve("project_atmosphere")
                .resolve("telemetry");
    }

    private Path buildArchivePath(Path telemetryDir, SessionHeader header) throws IOException {
        String paVersion = header.projectAtmosphereVersion;
        String mcVersion = header.minecraftVersion;
        String timestamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.ROOT).format(new Date());
        String sessionShort = header.sessionId.substring(0, Math.min(6, header.sessionId.length()));
        String baseName = String.format(Locale.ROOT, "pa_telemetry_%s_mc%s_%s_%s.zip", paVersion, mcVersion, timestamp, sessionShort);
        Path candidate = telemetryDir.resolve(baseName);
        int attempt = 1;
        while (Files.exists(candidate)) {
            candidate = telemetryDir.resolve(baseName.replace(".zip", "_" + (++attempt) + ".zip"));
        }
        return candidate;
    }

    private void zipDirectory(Path inputDir, Path archivePath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archivePath, StandardOpenOption.CREATE_NEW)));
             var paths = Files.walk(inputDir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        Path relative = inputDir.relativize(path);
                        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path))) {
                            ZipEntry entry = new ZipEntry(relative.toString().replace('\\', '/'));
                            zos.putNextEntry(entry);
                            bis.transferTo(zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private void pruneOldArchives(Path telemetryDir) {
        int retentionDays = AtmoCommonConfig.TELEMETRY_RETENTION_DAYS.get();
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86_400L);
        try (var paths = Files.list(telemetryDir)) {
            paths.filter(path -> path.getFileName().toString().startsWith("pa_telemetry") && path.toString().endsWith(".zip"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> {
                        try {
                            Instant lastModified = Files.getLastModifiedTime(path).toInstant();
                            if (lastModified.isBefore(cutoff)) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException ignored) {
                            ProjectAtmosphere.LOGGER.debug("Failed to check telemetry archive {}", path, ignored);
                        }
                    });
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.debug("Telemetry retention scan failed", e);
        }
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

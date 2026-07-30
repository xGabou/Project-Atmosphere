package net.Gabou.projectatmosphere.telemetry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.SessionHeader;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class TelemetryArchiveWriter {
    private TelemetryArchiveWriter() {
    }

    public static Path exportSnapshot(Path telemetryDir, TelemetryCollector collector) throws Exception {
        Files.createDirectories(telemetryDir);

        String sessionId = collector.getHeader().sessionId;
        Path sessionDir = telemetryDir.resolve("session_" + sessionId);
        collector.writeSnapshot(sessionDir);

        Path archivePath = buildArchivePath(telemetryDir, collector.getHeader());
        zipDirectory(sessionDir, archivePath);
        pruneOldArchives(telemetryDir);
        return archivePath;
    }

    public static Path exportSnapshot(Path telemetryDir, TelemetryCollector collector, MinecraftServer server) throws Exception {
        Files.createDirectories(telemetryDir);

        String sessionId = collector.getHeader().sessionId;
        Path sessionDir = telemetryDir.resolve("session_" + sessionId);

        ServerStateArchiveWriter.ServerStateSnapshot serverSnapshot = AsyncAtmosphereService.callOnMainThread(
                () -> ServerStateArchiveWriter.capture(server)
        );
        ServerStateArchiveWriter.write(sessionDir.resolve("server"), serverSnapshot);

        collector.writeSnapshot(sessionDir);

        Path archivePath = buildArchivePath(telemetryDir, collector.getHeader());
        zipDirectory(sessionDir, archivePath);
        pruneOldArchives(telemetryDir);
        return archivePath;
    }

    public static Path buildArchivePath(Path telemetryDir, SessionHeader header) throws IOException {
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

    private static void zipDirectory(Path inputDir, Path archivePath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archivePath)));
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

    private static void pruneOldArchives(Path telemetryDir) {
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
}

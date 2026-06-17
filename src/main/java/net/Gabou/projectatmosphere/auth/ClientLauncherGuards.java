package net.Gabou.projectatmosphere.auth;

import net.neoforged.fml.loading.FMLEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ClientLauncherGuards {
    private static final String SUSPICIOUS_FILE_NAME = "TLauncherAdditional.json";
    private static volatile String detectedReason;

    private ClientLauncherGuards() {
    }

    public static void enforce() {
        if (!FMLEnvironment.production) {
            detectedReason = null;
            return;
        }
        detectedReason = detectSuspiciousLauncher();
    }

    public static String detectSuspiciousLauncher() {
        return findSuspiciousFile() ? "file:" + SUSPICIOUS_FILE_NAME : null;
    }

    public static String getDetectedReason() {
        return detectedReason;
    }

    private static boolean findSuspiciousFile() {
        Path cwd = Paths.get(System.getProperty("user.dir", "."));
        Path candidate = cwd.resolve(SUSPICIOUS_FILE_NAME);
        return Files.exists(candidate);
    }
}

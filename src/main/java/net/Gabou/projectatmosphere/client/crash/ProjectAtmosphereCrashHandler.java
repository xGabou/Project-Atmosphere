package net.Gabou.projectatmosphere.client.crash;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.screen.ProjectAtmosphereCrashScreen;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

public final class ProjectAtmosphereCrashHandler {
    private static final String PROJECT_ATMOSPHERE_PACKAGE = "net.Gabou.projectatmosphere";
    private static final String DISCORD_INVITE_URL = "https://discord.gg/2jRhTJgYz4";
    private static final DateTimeFormatter REPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    @Nullable
    private static CrashContext activeCrash;

    private ProjectAtmosphereCrashHandler() {
    }

    public static boolean handleThrowable(Minecraft minecraft, Throwable throwable, String title) {
        if (!isProjectAtmosphereCrash(throwable)) {
            return false;
        }

        return handleCrashReport(minecraft, CrashReport.forThrowable(throwable, title));
    }

    public static boolean handleCrashReport(Minecraft minecraft, CrashReport report) {
        if (!isProjectAtmosphereCrash(report)) {
            return false;
        }

        if (activeCrash != null && minecraft.screen instanceof ProjectAtmosphereCrashScreen) {
            return true;
        }

        CrashReport enrichedReport = enrichReport(minecraft, report);
        CrashContext crashContext = buildCrashContext(minecraft, enrichedReport);
        activeCrash = crashContext;

        ProjectAtmosphere.LOGGER.error("Intercepted a Project Atmosphere client crash; opening the custom crash screen.");

        Screen crashScreen = new ProjectAtmosphereCrashScreen(crashContext);
        presentCrashScreen(minecraft, crashScreen);
        return true;
    }

    public static String getDiscordInviteUrl() {
        return DISCORD_INVITE_URL;
    }

    @Nullable
    public static CrashContext getActiveCrash() {
        return activeCrash;
    }

    public static boolean isProjectAtmosphereCrash(CrashReport report) {
        return isProjectAtmosphereCrash(report.getException());
    }

    public static boolean isProjectAtmosphereCrash(@Nullable Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> queue = new ArrayDeque<>();
        queue.add(throwable);

        while (!queue.isEmpty()) {
            Throwable current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }

            if (isProjectAtmosphereClass(current.getClass().getName()) || containsProjectAtmosphereFrame(current.getStackTrace())) {
                return true;
            }

            Throwable cause = current.getCause();
            if (cause != null) {
                queue.addLast(cause);
            }

            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed != null) {
                    queue.addLast(suppressed);
                }
            }
        }

        return false;
    }

    private static void presentCrashScreen(Minecraft minecraft, Screen crashScreen) {
        try {
            if (minecraft.level != null && tryTransitionMethod(minecraft, "clearClientLevel", crashScreen)) {
                return;
            }

            if (minecraft.level != null && tryTransitionMethod(minecraft, "disconnect", crashScreen)) {
                return;
            }

            if (minecraft.level == null || minecraft.screen != crashScreen) {
                minecraft.setScreen(crashScreen);
            }
        } catch (Throwable secondaryFailure) {
            ProjectAtmosphere.LOGGER.error("Failed to clear the client level while opening the Project Atmosphere crash screen.", secondaryFailure);
            minecraft.setScreen(crashScreen);
        }
    }

    private static boolean tryTransitionMethod(Minecraft minecraft, String methodName, Screen crashScreen) {
        try {
            Minecraft.class.getMethod(methodName, Screen.class).invoke(minecraft, crashScreen);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static CrashReport enrichReport(Minecraft minecraft, CrashReport report) {
        try {
            return minecraft.fillReport(report);
        } catch (Throwable secondaryFailure) {
            ProjectAtmosphere.LOGGER.error("Failed to enrich the Project Atmosphere crash report; falling back to the original report.", secondaryFailure);
            return report;
        }
    }

    private static CrashContext buildCrashContext(Minecraft minecraft, CrashReport report) {
        Throwable throwable = report.getException();
        StackTraceElement firstRelevantFrame = findFirstProjectAtmosphereFrame(throwable);
        Path reportPath = saveCrashReport(minecraft, report);
        String modVersion = ModList.get().getModContainerById(ProjectAtmosphere.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        String supportSummary = buildSupportSummary(report, throwable, firstRelevantFrame, reportPath, modVersion);

        return new CrashContext(
                report.getTitle(),
                formatThrowable(throwable),
                firstRelevantFrame == null ? "Unavailable" : firstRelevantFrame.toString(),
                reportPath == null ? "Unable to save crash report" : reportPath.toAbsolutePath().toString(),
                supportSummary
        );
    }

    @Nullable
    private static Path saveCrashReport(Minecraft minecraft, CrashReport report) {
        File existingSave = report.getSaveFile();
        if (existingSave != null) {
            return existingSave.toPath();
        }

        File reportFile = new File(
                new File(minecraft.gameDirectory, "crash-reports"),
                "crash-" + REPORT_TIMESTAMP.format(LocalDateTime.now()) + "-projectatmosphere-client.txt"
        );

        return report.saveToFile(reportFile) ? reportFile.toPath() : null;
    }

    @Nullable
    private static StackTraceElement findFirstProjectAtmosphereFrame(@Nullable Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> queue = new ArrayDeque<>();
        queue.add(throwable);

        while (!queue.isEmpty()) {
            Throwable current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }

            for (StackTraceElement element : current.getStackTrace()) {
                if (isProjectAtmosphereClass(element.getClassName())) {
                    return element;
                }
            }

            Throwable cause = current.getCause();
            if (cause != null) {
                queue.addLast(cause);
            }

            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed != null) {
                    queue.addLast(suppressed);
                }
            }
        }

        return null;
    }

    private static boolean containsProjectAtmosphereFrame(StackTraceElement[] stackTrace) {
        for (StackTraceElement element : stackTrace) {
            if (isProjectAtmosphereClass(element.getClassName())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isProjectAtmosphereClass(String className) {
        return className != null && className.startsWith(PROJECT_ATMOSPHERE_PACKAGE);
    }

    private static String buildSupportSummary(
            CrashReport report,
            Throwable throwable,
            @Nullable StackTraceElement firstRelevantFrame,
            @Nullable Path reportPath,
            String modVersion
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Project Atmosphere support request").append('\n');
        builder.append("Mod version: ").append(modVersion).append('\n');
        builder.append("Minecraft version: ").append(SharedConstants.getCurrentVersion().getName()).append('\n');
        builder.append("Crash title: ").append(report.getTitle()).append('\n');
        builder.append("Exception: ").append(formatThrowable(throwable)).append('\n');
        builder.append("Project Atmosphere frame: ")
                .append(firstRelevantFrame == null ? "Unavailable" : firstRelevantFrame)
                .append('\n');
        builder.append("Crash report: ")
                .append(reportPath == null ? "Unable to save crash report" : reportPath.toAbsolutePath())
                .append('\n');
        builder.append("Discord: ").append(DISCORD_INVITE_URL);
        return builder.toString();
    }

    private static String formatThrowable(@Nullable Throwable throwable) {
        if (throwable == null) {
            return "Unknown throwable";
        }

        String message = throwable.getMessage();
        String name = throwable.getClass().getName();
        return message == null || message.isBlank() ? name : name + ": " + message;
    }

    public record CrashContext(
            String crashTitle,
            String exceptionSummary,
            String projectAtmosphereFrame,
            String savedReportPath,
            String supportSummary
    ) {
    }
}

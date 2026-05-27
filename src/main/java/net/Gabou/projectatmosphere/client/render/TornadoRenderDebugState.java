package net.Gabou.projectatmosphere.client.render;

import net.Gabou.projectatmosphere.ProjectAtmosphere;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public final class TornadoRenderDebugState {
    public enum Mode {
        OFF("off", 0),
        BOX("box", 1),
        HIT("hit", 2),
        FILL("fill", 3),
        FUNNEL("funnel", 4),
        HEIGHT("height", 5),
        RADIAL("radial", 6),
        RADIUS("radius", 7),
        DENSITY("density", 8),
        ALPHA("alpha", 9),
        WALLCLOUD("wallcloud", 10),
        CONNECTION("connection", 11),
        GROUND_SKIRT("groundskirt", 12),
        FULL("full", 13),
        DEPTH("depth", 14),
        DEPTH_NO_FRAMEBUFFER("depth_nofb", 15),
        DEPTH_MAIN_FRAMEBUFFER("depth_mainfb", 14),
        OCCLUSION("occlusion", 16),
        COVERAGE("coverage", 17),
        LATE("late", 0);

        private final String token;
        private final int shaderValue;

        Mode(String token, int shaderValue) {
            this.token = token;
            this.shaderValue = shaderValue;
        }

        public String token() {
            return this.token;
        }

        public int shaderValue() {
            return this.shaderValue;
        }

        public static Mode fromToken(String token) {
            if (token == null) {
                return OFF;
            }
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("aabb")) {
                return BOX;
            }
            for (Mode mode : values()) {
                if (mode.token.equals(normalized)) {
                    return mode;
                }
            }
            return OFF;
        }
    }

    private static Mode mode = Mode.OFF;
    private static boolean freeze;
    private static int requestedStormIndex = -1;
    private static boolean diagnosticReportRequested;

    private TornadoRenderDebugState() {
    }

    public static synchronized Mode getMode() {
        return mode;
    }

    public static synchronized void setMode(Mode newMode) {
        mode = newMode == null ? Mode.OFF : newMode;
    }

    public static synchronized boolean isFreezeEnabled() {
        return freeze;
    }

    public static synchronized void setFreezeEnabled(boolean enabled) {
        freeze = enabled;
    }

    public static synchronized int getRequestedStormIndex() {
        return requestedStormIndex;
    }

    public static synchronized void setRequestedStormIndex(int index) {
        requestedStormIndex = Math.max(-1, index);
    }

    public static synchronized boolean isActive() {
        return ProjectAtmosphere.DEBUG_MODE && mode != Mode.OFF;
    }

    public static synchronized boolean isCommandAvailable() {
        return ProjectAtmosphere.DEBUG_MODE;
    }

    public static synchronized void requestDiagnosticReport() {
        diagnosticReportRequested = true;
    }

    public static synchronized boolean consumeDiagnosticReportRequest() {
        boolean requested = diagnosticReportRequested;
        diagnosticReportRequested = false;
        return requested;
    }

    public static String supportedModes() {
        return Arrays.stream(Mode.values())
                .map(Mode::token)
                .collect(Collectors.joining(", "));
    }

    public static synchronized String describe() {
        String storm = requestedStormIndex < 0 ? "auto" : Integer.toString(requestedStormIndex);
        return "mode=" + mode.token() + ", freeze=" + freeze + ", storm=" + storm;
    }
}

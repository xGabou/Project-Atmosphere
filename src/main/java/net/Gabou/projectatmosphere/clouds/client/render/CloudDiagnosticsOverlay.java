package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.CloudWeatherSample;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowSnapshot;
import net.Gabou.projectatmosphere.clouds.client.ClientLocalizedWeatherState;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.CloudWeatherMapRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudFrameDiagnostics;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderer;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereWorldEffectsDiagnostics;
import net.Gabou.projectatmosphere.tools.debug.HurricaneRenderDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CloudDiagnosticsOverlay {
    private static final int BACKGROUND_COLOR = 0xA0101010;
    private static final int TEXT_COLOR = 0xFFE6F2FF;
    private static final int MUTED_TEXT_COLOR = 0xFFB8C6D0;

    private static final int SCREEN_MARGIN = 6;
    private static final int HOTBAR_CLEARANCE = 30;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 4;

    private static final long REFRESH_INTERVAL_NS = 250_000_000L;

    private static final int MAX_COMPACT_LINES = 5;
    private static final int MAX_FULL_LINES = 8;

    private static List<FormattedCharSequence> cachedVisualLines = Collections.emptyList();
    private static int cachedWidth;
    private static int cachedMaxTextWidth;
    private static long lastCacheRefreshNs;
    private static AtmoCommonConfig.CloudDiagnosticsOverlayMode cachedMode;

    private static int callsThisSecond = 0;
    private static long lastPrint = 0L;


    private CloudDiagnosticsOverlay() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.DEBUG_OVERLAY)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        AtmoCommonConfig.CloudDiagnosticsOverlayMode mode = getMode();

        if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.OFF) {
            return;
        }

        Font font = minecraft.font;
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int maxTextWidth = Math.max(
                40,
                guiGraphics.guiWidth() - (SCREEN_MARGIN + PADDING) * 2
        );
        List<FormattedCharSequence> lines = getCachedVisualLines(font, mode, maxTextWidth);

        if (lines.isEmpty()) {
            return;
        }

        int height = lines.size() * LINE_HEIGHT + PADDING * 2;
        int x = Math.max(
                SCREEN_MARGIN + PADDING,
                guiGraphics.guiWidth() - cachedWidth - PADDING - SCREEN_MARGIN
        );
        int y = Math.max(
                SCREEN_MARGIN + PADDING,
                guiGraphics.guiHeight() - height - HOTBAR_CLEARANCE + PADDING
        );

        guiGraphics.fill(
                x - PADDING,
                y - PADDING,
                x + cachedWidth + PADDING,
                y + height - PADDING,
                BACKGROUND_COLOR
        );

        drawLines(guiGraphics, font, lines, x, y);
    }

    /**
     * Gets the cached diagnostic overlay lines.
     *
     * @param font The Minecraft font renderer used for text measurements.
     * @param mode The current diagnostics overlay mode.
     * @return The cached formatted lines ready to render.
     */
    private static List<FormattedCharSequence> getCachedVisualLines(
            Font font,
            AtmoCommonConfig.CloudDiagnosticsOverlayMode mode,
            int maxTextWidth
    ) {
        long nowNs = System.nanoTime();

        if (
                cachedMode == mode
                        && cachedMaxTextWidth == maxTextWidth
                        && nowNs - lastCacheRefreshNs < REFRESH_INTERVAL_NS
                        && !cachedVisualLines.isEmpty()
        ) {
            return cachedVisualLines;
        }

        List<String> rawLines = buildLines(mode);
        List<FormattedCharSequence> visualLines = new ArrayList<>(rawLines.size());

        int width = 0;

        for (String line : rawLines) {
            String fittedLine = fitLine(font, line, maxTextWidth);
            width = Math.max(width, font.width(fittedLine));
            visualLines.add(FormattedCharSequence.forward(fittedLine, Style.EMPTY));
        }

        cachedVisualLines = visualLines;
        cachedWidth = width;
        cachedMaxTextWidth = maxTextWidth;
        cachedMode = mode;
        lastCacheRefreshNs = nowNs;

        return cachedVisualLines;
    }

    /**
     * Draws the cached diagnostic lines without forcing an immediate GUI flush.
     *
     * @param guiGraphics The current GUI graphics context.
     * @param font The Minecraft font renderer.
     * @param lines The cached formatted lines to draw.
     */
    private static void drawLines(
            GuiGraphics guiGraphics,
            Font font,
            List<FormattedCharSequence> lines,
            int x,
            int y
    ) {
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? TEXT_COLOR : MUTED_TEXT_COLOR;
            guiGraphics.drawString(font, lines.get(i), x, y + i * LINE_HEIGHT, color, false);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_O) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.getWindow() == null) {
            return;
        }

        long window = minecraft.getWindow().getWindow();

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F3) != GLFW.GLFW_PRESS) {
            return;
        }

        AtmoCommonConfig.CloudDiagnosticsOverlayMode next = getMode().next();

        AtmoCommonConfig.CLOUD_DIAGNOSTICS_OVERLAY.set(next);
        saveCommonConfigForMod(ProjectAtmosphere.MODID);
        invalidateCache();

        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("PA cloud overlay: " + next.name()), true);
        }
    }

    /**
     * Clears all cached overlay lines and measurements.
     */
    private static void invalidateCache() {
        cachedVisualLines = Collections.emptyList();
        cachedWidth = 0;
        cachedMaxTextWidth = 0;
        lastCacheRefreshNs = 0L;
        cachedMode = null;
    }

    /**
     * Builds a lightweight diagnostic overlay.
     *
     * @param mode The current diagnostics overlay mode.
     * @return A capped list of raw diagnostic lines.
     */
    private static List<String> buildLines(AtmoCommonConfig.CloudDiagnosticsOverlayMode mode) {
        List<String> lines = new ArrayList<>();
        boolean full = mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL;
        int maxLines = full ? MAX_FULL_LINES : MAX_COMPACT_LINES;

        lines.add("PA [" + mode.name() + "]");

        VolumetricCloudFrameDiagnostics.Snapshot stats = VolumetricCloudFrameDiagnostics.latest();

        if (showRenderSection() && lines.size() < maxLines) {
            if (full) {
                lines.add("Render " + stats.qualityProfile()
                        + " | target " + abbreviate(stats.cloudTargetSize(), 30)
                        + " | active=" + yesNo(stats.rendererActive()));
            } else {
                lines.add("Render: " + stats.qualityProfile()
                        + " / " + stats.renderCellCount() + " cells"
                        + " / history " + yesNo(stats.historyConsumedThisFrame()));
            }
        }

        if (showRenderSection() && lines.size() < maxLines) {
            if (full) {
                lines.add("Clouds fields=" + stats.fieldsReceived()
                        + " cells=" + stats.renderCellCount()
                        + " splat=" + stats.weather().cloudletsSplatted()
                        + " dropped=" + stats.weather().cloudletsDroppedBeforeSplat());
            } else {
                lines.add("Clouds: " + stats.fieldsReceived()
                        + " fields / " + stats.weather().cloudletsSplatted()
                        + " cloudlets");
            }
        }

        if (showRenderSection() && full && lines.size() < maxLines) {
            lines.add("GPU ray=" + formatFloat(VolumetricCloudRenderer.lastGpuMilliseconds())
                    + "ms | history=" + formatFloat(VolumetricCloudRenderer.lastHistoryConfidence())
                    + " | " + CloudWeatherMapRenderer.cacheStatus());
        }

        if (showRenderSection() && full && lines.size() < maxLines) {
            lines.add("Depth scene=" + yesNo(stats.sceneDepthAvailable())
                    + " tex=" + stats.sceneDepthTextureId()
                    + " | composite=" + stats.compositeMode());
        }

        if (showRenderSection() && full && lines.size() < maxLines) {
            CloudShadowSnapshot shadow = CloudShadowMapAccess.getCurrentSnapshot();

            if (shadow != null) {
                lines.add("Shadow " + yesNo(shadow.isValid())
                        + " | tex=" + shadow.getTextureId()
                        + " | grid=" + shadow.getResolutionX() + "x" + shadow.getResolutionZ()
                        + " | frame=" + shadow.getValidityFrame());
            }
        }

        appendHurricaneLine(lines, mode, maxLines);

        if (showWeatherSection() && lines.size() < maxLines) {
            ClientLocalizedWeatherState.Diagnostics weather = ClientLocalizedWeatherState.getDiagnostics();

            if (weather != null) {
                if (full) {
                    CloudWeatherSample sample = weather.sample();
                    BlockPos pos = weather.samplePos();

                    lines.add("Weather r " + formatFloat(weather.targetRainLevel())
                            + ">" + formatFloat(weather.smoothedRainLevel())
                            + " t " + formatFloat(weather.targetThunderLevel())
                            + ">" + formatFloat(weather.smoothedThunderLevel())
                            + " | " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                            + " | c=" + formatFloat(sample.cloudCoverStrength()));
                } else {
                    lines.add("Weather: rain " + formatFloat(weather.targetRainLevel())
                            + " > " + formatFloat(weather.smoothedRainLevel())
                            + " / thunder " + formatFloat(weather.targetThunderLevel())
                            + " > " + formatFloat(weather.smoothedThunderLevel()));
                }
            }
        }

        if (showWeatherSection() && full && lines.size() < maxLines) {
            ClientLocalizedWeatherState.Diagnostics weather = ClientLocalizedWeatherState.getDiagnostics();

            if (weather != null) {
                CloudWeatherSample sample = weather.sample();

                if (sample != null) {
                    lines.add("Src " + abbreviate(sample.describeSource(), 34)
                            + " | col=" + yesNo(sample.inPrecipitationColumn())
                            + " snow=" + yesNo(sample.snowing()));
                }
            }
        }

        if (showWeatherSection() && full && lines.size() < maxLines) {
            PrecipitationVisualState precip = CustomPrecipitationRenderer.getLastState();

            if (precip != null) {
                lines.add("Precip " + precip.rainTier().name()
                        + "/" + precip.snowTier().name()
                        + " | fog=" + formatFloat(precip.fogBoost())
                        + " | splash=" + formatFloat(precip.splashIntensity()));
            }
        }

        if (showWorldEffectsSection() && lines.size() < maxLines) {
            AtmosphereWorldEffectsDiagnostics.FrameStats effects = AtmosphereWorldEffectsDiagnostics.getLastStats();

            if (effects != null) {
                if (full) {
                    lines.add("Effects " + yesNo(effects.enabled())
                            + " | samples " + effects.rainySamples()
                            + "/" + effects.samples()
                            + " | rain=" + formatFloat(effects.lastRainIntensity()));
                } else {
                    lines.add("Effects: " + yesNo(effects.enabled())
                            + " / samples " + effects.rainySamples()
                            + "/" + effects.samples()
                            + " / rain=" + formatFloat(effects.lastRainIntensity()));
                }
            }
        }

        return limitLines(lines, maxLines);
    }

    /**
     * Adds the hurricane diagnostic line when there is enough overlay space.
     *
     * @param lines The current mutable line list.
     * @param mode The current diagnostics overlay mode.
     * @param maxLines The maximum number of lines allowed.
     */
    private static void appendHurricaneLine(
            List<String> lines,
            AtmoCommonConfig.CloudDiagnosticsOverlayMode mode,
            int maxLines
    ) {
        if (lines.size() >= maxLines) {
            return;
        }

        HurricaneRenderDiagnostics.FrameStats stats = HurricaneRenderDiagnostics.getLastStats();
        boolean detailed = mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL;

        lines.add("Hurricane " + (stats == null ? "idle" : stats.describe(detailed)));
    }

    /**
     * Limits the amount of rendered text to keep the debug overlay cheap.
     *
     * @param lines The original diagnostic lines.
     * @param maxLines The maximum number of lines to keep.
     * @return The capped diagnostic line list.
     */
    private static List<String> limitLines(List<String> lines, int maxLines) {
        if (lines.size() <= maxLines) {
            return lines;
        }

        return new ArrayList<>(lines.subList(0, maxLines));
    }

    /**
     * Gets the current diagnostics overlay mode.
     *
     * @return The active overlay mode, or OFF if the config is unavailable.
     */
    private static AtmoCommonConfig.CloudDiagnosticsOverlayMode getMode() {
        try {
            return AtmoCommonConfig.CLOUD_DIAGNOSTICS_OVERLAY.get();
        } catch (IllegalStateException exception) {
            return AtmoCommonConfig.CloudDiagnosticsOverlayMode.OFF;
        }
    }

    /**
     * Checks if render diagnostics should be shown.
     *
     * @return True when render diagnostics are enabled.
     */
    private static boolean showRenderSection() {
        try {
            return AtmoCommonConfig.CLOUD_DIAGNOSTICS_SHOW_RENDER.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    /**
     * Checks if weather diagnostics should be shown.
     *
     * @return True when weather diagnostics are enabled.
     */
    private static boolean showWeatherSection() {
        try {
            return AtmoCommonConfig.CLOUD_DIAGNOSTICS_SHOW_WEATHER.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    /**
     * Checks if world effect diagnostics should be shown.
     *
     * @return True when world effect diagnostics are enabled.
     */
    private static boolean showWorldEffectsSection() {
        try {
            return AtmoCommonConfig.CLOUD_DIAGNOSTICS_SHOW_WORLD_EFFECTS.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    /**
     * Formats a float as a percentage.
     *
     * @param value The value to format.
     * @return The formatted percentage.
     */
    private static String percent(float value) {
        return Math.round(value * 100.0F) + "%";
    }

    /**
     * Converts a boolean into a compact yes or no value.
     *
     * @param value The boolean value.
     * @return yes when true, otherwise no.
     */
    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    /**
     * Formats a float with two decimals.
     *
     * @param value The value to format.
     * @return The formatted float.
     */
    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Formats a GPU timing value.
     *
     * @param value The timing value in milliseconds.
     * @param supported Whether GPU timings are supported.
     * @param valid Whether the timing value is valid.
     * @param ageFrames The query age in frames.
     * @param pendingQueries The amount of pending GPU timing queries.
     * @return The formatted timing string.
     */
    private static String formatTiming(
            float value,
            boolean supported,
            boolean valid,
            int ageFrames,
            int pendingQueries
    ) {
        if (!supported || !valid) {
            return "n/a";
        }

        return formatFloat(value) + "ms a" + ageFrames + " p" + pendingQueries;
    }

    /**
     * Abbreviates a string to a maximum length.
     *
     * @param value The string to abbreviate.
     * @param maxLength The maximum allowed length.
     * @return The abbreviated string.
     */
    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }

        return value.substring(0, Math.max(0, maxLength - 1)) + "~";
    }

    private static String fitLine(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        return font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width(suffix))) + suffix;
    }

    /**
     * Saves the common config for the requested mod id.
     *
     * @param modId The target mod id.
     */
    private static void saveCommonConfigForMod(String modId) {
        try {
            AtmoCommonConfig.COMMON_SPEC.save();
        } catch (Exception ignored) {
        }
    }
}

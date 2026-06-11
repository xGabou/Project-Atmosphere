package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.CloudWeatherSample;
import net.Gabou.projectatmosphere.clouds.client.ClientLocalizedWeatherState;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereWorldEffectsDiagnostics;
import net.Gabou.projectatmosphere.tools.debug.HurricaneRenderDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public final class CloudDiagnosticsOverlay {
    private static final int BACKGROUND_COLOR = 0xA0101010;
    private static final int TEXT_COLOR = 0xFFE6F2FF;
    private static final int MUTED_TEXT_COLOR = 0xFFB8C6D0;
    private static final int X = 6;
    private static final int Y = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 4;
    private static final long REFRESH_INTERVAL_NS = 100_000_000L;
    private static final int GUI_PACKED_LIGHT = 0xF000F0;

    private static List<String> cachedLines = Collections.emptyList();
    private static int cachedWidth;
    private static long lastCacheRefreshNs;

    private CloudDiagnosticsOverlay() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        AtmoCommonConfig.CloudDiagnosticsOverlayMode mode = getMode();
        if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.OFF) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<String> lines = getCachedLines(font, mode);
        if (lines.isEmpty()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int height = lines.size() * LINE_HEIGHT + PADDING * 2;
        guiGraphics.fill(X - PADDING, Y - PADDING, X + cachedWidth + PADDING, Y + height - PADDING, BACKGROUND_COLOR);
        drawLinesBatched(guiGraphics, font, lines);
    }

    private static List<String> getCachedLines(Font font, AtmoCommonConfig.CloudDiagnosticsOverlayMode mode) {
        long nowNs = System.nanoTime();
        if (nowNs - lastCacheRefreshNs < REFRESH_INTERVAL_NS && !cachedLines.isEmpty()) {
            return cachedLines;
        }

        CloudRenderDiagnostics.FrameStats stats = CloudRenderDiagnostics.getLastStats();
        List<String> lines = buildLines(stats, mode);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }

        cachedLines = lines;
        cachedWidth = width;
        lastCacheRefreshNs = nowNs;
        return cachedLines;
    }

    private static void drawLinesBatched(GuiGraphics guiGraphics, Font font, List<String> lines) {
        var pose = guiGraphics.pose().last().pose();
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? TEXT_COLOR : MUTED_TEXT_COLOR;
            font.drawInBatch(
                    lines.get(i),
                    X,
                    Y + i * LINE_HEIGHT,
                    color,
                    false,
                    pose,
                    guiGraphics.bufferSource(),
                    Font.DisplayMode.NORMAL,
                    0,
                    GUI_PACKED_LIGHT
            );
        }
        guiGraphics.flush();
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

    private static void invalidateCache() {
        cachedLines = Collections.emptyList();
        cachedWidth = 0;
        lastCacheRefreshNs = 0L;
    }

    private static List<String> buildLines(
            CloudRenderDiagnostics.FrameStats stats,
            AtmoCommonConfig.CloudDiagnosticsOverlayMode mode
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("PA [" + mode.name() + "]");

        if (showRenderSection()) {
            if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL) {
                lines.add("Render " + stats.qualityName()
                        + " " + stats.raymarchSteps() + "s "
                        + percent(stats.resolutionScale())
                        + " | " + stats.targetWidth() + "x" + stats.targetHeight()
                        + "/" + stats.mainWidth() + "x" + stats.mainHeight()
                        + " | ds=" + yesNo(stats.downscaled()));
                lines.add("Clouds " + stats.renderedSnapshots()
                        + "/" + stats.renderableSnapshots()
                        + "/" + stats.sourceSnapshots()
                        + " | skip " + stats.totalSkippedSnapshots()
                        + "/" + stats.filteredSkippedSnapshots()
                        + "/" + stats.submitSkippedSnapshots()
                        + " | comp=" + yesNo(stats.compositeSubmitted()));
                lines.add("Work " + formatFloat(stats.pixelStepMegas()) + "M"
                        + " | CPU " + formatFloat(stats.frameCpuMs())
                        + "/" + formatFloat(stats.raymarchCpuMs())
                        + "/" + formatFloat(stats.compositeCpuMs()) + "ms");
                lines.add("GPU ray " + formatTiming(
                        stats.raymarchGpuMs(),
                        stats.gpuTimingSupported(),
                        stats.raymarchGpuTimingValid(),
                        stats.raymarchGpuAgeFrames(),
                        stats.raymarchGpuPendingQueries()
                ) + " | comp " + formatTiming(
                        stats.compositeGpuMs(),
                        stats.gpuTimingSupported(),
                        stats.compositeGpuTimingValid(),
                        stats.compositeGpuAgeFrames(),
                        stats.compositeGpuPendingQueries()
                ));
                lines.add("Last " + stats.describeLastCloud() + " | t=" + stats.worldTime());
            } else {
                lines.add("Render: " + stats.qualityName() + " / " + stats.raymarchSteps() + " steps / " + percent(stats.resolutionScale()));
                lines.add("Target: " + stats.targetWidth() + "x" + stats.targetHeight()
                        + " of " + stats.mainWidth() + "x" + stats.mainHeight()
                        + " / downscaled=" + yesNo(stats.downscaled()));
                lines.add("Clouds: " + stats.renderedSnapshots()
                        + " rendered / " + stats.renderableSnapshots()
                        + " renderable / " + stats.sourceSnapshots() + " synced");
            }

        }

        lines.add(buildHurricaneLine(mode));

        if (showWeatherSection()) {
            ClientLocalizedWeatherState.Diagnostics weather = ClientLocalizedWeatherState.getDiagnostics();
            CloudWeatherSample sample = weather.sample();
            if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL) {
                BlockPos pos = weather.samplePos();
                lines.add("Weather r " + formatFloat(weather.targetRainLevel()) + ">" + formatFloat(weather.smoothedRainLevel())
                        + " t " + formatFloat(weather.targetThunderLevel()) + ">" + formatFloat(weather.smoothedThunderLevel())
                        + " | " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " | c=" + formatFloat(sample.cloudCoverStrength()));
                lines.add("Src " + abbreviate(sample.describeSource(), 34)
                        + " | col=" + yesNo(sample.inPrecipitationColumn())
                        + " snow=" + yesNo(sample.snowing()));
            } else {
                lines.add("Weather: rain " + formatFloat(weather.targetRainLevel()) + " -> " + formatFloat(weather.smoothedRainLevel())
                        + " / thunder " + formatFloat(weather.targetThunderLevel()) + " -> " + formatFloat(weather.smoothedThunderLevel()));
            }
        }

        if (showWorldEffectsSection()) {
            AtmosphereWorldEffectsDiagnostics.FrameStats effects = AtmosphereWorldEffectsDiagnostics.getLastStats();
            if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL) {
                lines.add("Effects " + yesNo(effects.enabled())
                        + " | samples " + effects.rainySamples() + "/" + effects.samples()
                        + " | rain=" + formatFloat(effects.lastRainIntensity())
                        + " | hooks " + effects.eventHooks() + "/" + effects.customHooks() + "/" + effects.skyBlockedSamples());
                lines.add("Blocks f/c/ca " + effects.firesRemoved()
                        + "/" + effects.campfiresDoused()
                        + "/" + effects.cauldronsFilled());
            } else {
                lines.add("Effects: enabled=" + yesNo(effects.enabled())
                        + " / samples " + effects.rainySamples() + "/" + effects.samples()
                        + " / lastRain=" + formatFloat(effects.lastRainIntensity()));
            }
        }

        return lines;
    }

    private static String buildHurricaneLine(AtmoCommonConfig.CloudDiagnosticsOverlayMode mode) {
        HurricaneRenderDiagnostics.FrameStats stats = HurricaneRenderDiagnostics.getLastStats();
        boolean detailed = mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL;
        return "Hurricane " + (stats == null ? "idle" : stats.describe(detailed));
    }

    private static AtmoCommonConfig.CloudDiagnosticsOverlayMode getMode() {
        try {
            return AtmoCommonConfig.CLOUD_DIAGNOSTICS_OVERLAY.get();
        } catch (IllegalStateException exception) {
            return AtmoCommonConfig.CloudDiagnosticsOverlayMode.OFF;
        }
    }

    private static boolean showRenderSection() {
        try {
            return AtmoCommonConfig.CLOUD_DIAGNOSTICS_SHOW_RENDER.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private static boolean showWeatherSection() {
        try {
            return AtmoCommonConfig.CLOUD_DIAGNOSTICS_SHOW_WEATHER.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private static boolean showWorldEffectsSection() {
        try {
            return AtmoCommonConfig.CLOUD_DIAGNOSTICS_SHOW_WORLD_EFFECTS.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private static String percent(float value) {
        return Math.round(value * 100.0F) + "%";
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String formatFloat(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String formatTiming(float value, boolean supported, boolean valid, int ageFrames, int pendingQueries) {
        if (!supported || !valid) {
            return "n/a";
        }
        return formatFloat(value) + "ms a" + ageFrames + " p" + pendingQueries;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }

        return value.substring(0, Math.max(0, maxLength - 1)) + "~";
    }

    private static void saveCommonConfigForMod(String modId) {
        try {
            var set = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON);
            if (set == null) {
                return;
            }

            for (ModConfig cfg : set) {
                if (cfg.getModId().equals(modId)) {
                    cfg.save();
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }
}

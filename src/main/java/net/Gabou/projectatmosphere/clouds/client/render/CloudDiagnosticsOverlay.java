package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.CloudWeatherSample;
import net.Gabou.projectatmosphere.clouds.client.ClientLocalizedWeatherState;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereWorldEffectsDiagnostics;
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

    private CloudDiagnosticsOverlay() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        AtmoCommonConfig.CloudDiagnosticsOverlayMode mode = getMode();
        if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.OFF) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.renderDebug) {
            return;
        }

        CloudRenderDiagnostics.FrameStats stats = CloudRenderDiagnostics.getLastStats();
        List<String> lines = buildLines(stats, mode);
        if (lines.isEmpty()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }

        int height = lines.size() * LINE_HEIGHT + PADDING * 2;
        guiGraphics.fill(X - PADDING, Y - PADDING, X + width + PADDING, Y + height - PADDING, BACKGROUND_COLOR);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? TEXT_COLOR : MUTED_TEXT_COLOR;
            guiGraphics.drawString(font, lines.get(i), X, Y + i * LINE_HEIGHT, color, false);
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

        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("PA cloud overlay: " + next.name()), true);
        }
    }

    private static List<String> buildLines(
            CloudRenderDiagnostics.FrameStats stats,
            AtmoCommonConfig.CloudDiagnosticsOverlayMode mode
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("PA Overlay [" + mode.name() + "]");

        if (showRenderSection()) {
            lines.add("Render: " + stats.qualityName() + " / " + stats.raymarchSteps() + " steps / " + percent(stats.resolutionScale()));
            lines.add("Target: " + stats.targetWidth() + "x" + stats.targetHeight()
                    + " of " + stats.mainWidth() + "x" + stats.mainHeight()
                    + " / downscaled=" + yesNo(stats.downscaled()));
            lines.add("Clouds: " + stats.renderedSnapshots()
                    + " rendered / " + stats.renderableSnapshots()
                    + " renderable / " + stats.sourceSnapshots() + " synced");

            if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL) {
                lines.add("Work: " + formatFloat(stats.pixelStepMegas()) + "M px-steps");
                lines.add("Timing CPU: frame " + formatFloat(stats.frameCpuMs())
                        + "ms / ray " + formatFloat(stats.raymarchCpuMs())
                        + "ms / comp " + formatFloat(stats.compositeCpuMs()) + "ms");
                lines.add("Timing GPU: ray " + formatTiming(
                        stats.raymarchGpuMs(),
                        stats.gpuTimingSupported(),
                        stats.raymarchGpuTimingValid(),
                        stats.raymarchGpuAgeFrames(),
                        stats.raymarchGpuPendingQueries()
                ) + " / comp " + formatTiming(
                        stats.compositeGpuMs(),
                        stats.gpuTimingSupported(),
                        stats.compositeGpuTimingValid(),
                        stats.compositeGpuAgeFrames(),
                        stats.compositeGpuPendingQueries()
                ));
                lines.add("Skipped: " + stats.totalSkippedSnapshots()
                        + " total / " + stats.filteredSkippedSnapshots()
                        + " filtered / " + stats.submitSkippedSnapshots() + " submit");
                lines.add("Composite: " + yesNo(stats.compositeSubmitted()));
                lines.add("Last cloud: " + stats.describeLastCloud());
                lines.add("World time: " + stats.worldTime());
            }
        }

        if (showWeatherSection()) {
            ClientLocalizedWeatherState.Diagnostics weather = ClientLocalizedWeatherState.getDiagnostics();
            CloudWeatherSample sample = weather.sample();
            lines.add("Weather: rain " + formatFloat(weather.targetRainLevel()) + " -> " + formatFloat(weather.smoothedRainLevel())
                    + " / thunder " + formatFloat(weather.targetThunderLevel()) + " -> " + formatFloat(weather.smoothedThunderLevel()));

            if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL) {
                BlockPos pos = weather.samplePos();
                lines.add("Weather src: " + sample.describeSource()
                        + " / column=" + yesNo(sample.inPrecipitationColumn())
                        + " / snow=" + yesNo(sample.snowing()));
                lines.add("Weather pos: " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " / cover=" + formatFloat(sample.cloudCoverStrength()));
            }
        }

        if (showWorldEffectsSection()) {
            AtmosphereWorldEffectsDiagnostics.FrameStats effects = AtmosphereWorldEffectsDiagnostics.getLastStats();
            lines.add("Effects: enabled=" + yesNo(effects.enabled())
                    + " / samples " + effects.rainySamples() + "/" + effects.samples()
                    + " / lastRain=" + formatFloat(effects.lastRainIntensity()));

            if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL) {
                lines.add("Effects blocks: fire " + effects.firesRemoved()
                        + " / campfire " + effects.campfiresDoused()
                        + " / cauldron " + effects.cauldronsFilled());
                lines.add("Effects hooks: events " + effects.eventHooks()
                        + " / custom " + effects.customHooks()
                        + " / skyBlocked " + effects.skyBlockedSamples());
            }
        }

        return lines;
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

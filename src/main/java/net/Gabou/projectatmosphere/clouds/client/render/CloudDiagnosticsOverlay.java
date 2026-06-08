package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
        lines.add("PA Clouds [" + mode.name() + "]");
        lines.add("Quality: " + stats.qualityName() + " / " + stats.raymarchSteps() + " steps / " + percent(stats.resolutionScale()));
        lines.add("Target: " + stats.targetWidth() + "x" + stats.targetHeight()
                + " of " + stats.mainWidth() + "x" + stats.mainHeight()
                + " / downscaled=" + yesNo(stats.downscaled()));
        lines.add("Clouds: " + stats.renderedSnapshots()
                + " rendered / " + stats.renderableSnapshots()
                + " renderable / " + stats.sourceSnapshots() + " synced");

        if (mode == AtmoCommonConfig.CloudDiagnosticsOverlayMode.FULL) {
            lines.add("Skipped: " + stats.totalSkippedSnapshots()
                    + " total / " + stats.filteredSkippedSnapshots()
                    + " filtered / " + stats.submitSkippedSnapshots() + " submit");
            lines.add("Composite: " + yesNo(stats.compositeSubmitted()));
            lines.add("Last: " + stats.describeLastCloud());
            lines.add("World time: " + stats.worldTime());
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

    private static String percent(float value) {
        return Math.round(value * 100.0F) + "%";
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
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

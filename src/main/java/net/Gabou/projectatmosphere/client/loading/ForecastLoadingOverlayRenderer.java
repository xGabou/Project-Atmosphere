package net.Gabou.projectatmosphere.client.loading;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public final class ForecastLoadingOverlayRenderer {
    private static final String TITLE = "Project Atmosphere";
    private static final String[] DOTS = {"", ".", "..", "..."};
    private static final String WAITING_HINT = "Large modpacks may take longer.";
    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_VERTICAL_OFFSET = 56;
    private static final int PANEL_FILL_TOP = FastColor.ARGB32.color(214, 9, 14, 20);
    private static final int PANEL_FILL_BOTTOM = FastColor.ARGB32.color(198, 15, 20, 28);
    private static final int PANEL_OUTLINE = FastColor.ARGB32.color(255, 110, 158, 214);
    private static final int PANEL_INNER = FastColor.ARGB32.color(105, 188, 221, 255);
    private static final int TITLE_COLOR = 0xD8E9F8;
    private static final int STAGE_COLOR = 0xF5FAFF;
    private static final int TEXT_COLOR = 0xD9E5F1;
    private static final int SUBTEXT_COLOR = 0xAFC2D7;
    private static final int BAR_BG = FastColor.ARGB32.color(255, 17, 24, 31);
    private static final int BAR_FILL = FastColor.ARGB32.color(255, 111, 197, 255);
    private static final int BAR_FILL_SOFT = FastColor.ARGB32.color(255, 64, 136, 200);
    private static final int BAR_GLOW = FastColor.ARGB32.color(255, 210, 239, 255);
    private static final int PERCENT_COLOR = 0xCBE7FF;

    private ForecastLoadingOverlayRenderer() {
    }

    public static void render(GuiGraphics guiGraphics) {
        ForecastLoadingState.Snapshot snapshot = ForecastLoadingState.snapshot();
        if (!snapshot.active()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        long now = Util.getMillis();

        String stageLabel = snapshot.stage().displayName();
        String detail = buildDetail(snapshot, now);
        String hint = buildHint(snapshot);
        int titleWidth = font.width(TITLE);
        int stageWidth = font.width(stageLabel);
        int detailWidth = detail == null ? 0 : font.width(detail);
        int hintWidth = hint == null ? 0 : font.width(hint);
        int contentWidth = Math.max(Math.max(Math.max(titleWidth, stageWidth), detailWidth), hintWidth);
        int availableWidth = Math.max(1, screenWidth - PANEL_MARGIN * 2);
        int minPanelWidth = Math.min(220, availableWidth);
        int maxPanelWidth = Math.min(320, availableWidth);
        int panelWidth = Mth.clamp(contentWidth + 34, minPanelWidth, maxPanelWidth);
        int panelHeight = hint == null ? (detail == null ? 48 : 60) : 72;
        int maxTextWidth = Math.max(1, panelWidth - 28);

        stageLabel = fitText(font, stageLabel, maxTextWidth);
        detail = fitText(font, detail, maxTextWidth);
        hint = fitText(font, hint, maxTextWidth);

        int left = (screenWidth - panelWidth) / 2;
        int maxTop = Math.max(PANEL_MARGIN, screenHeight - panelHeight - PANEL_MARGIN);
        int top = Mth.clamp(
                (screenHeight - panelHeight) / 2 + PANEL_VERTICAL_OFFSET,
                PANEL_MARGIN,
                maxTop
        );
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        guiGraphics.fillGradient(left, top, right, bottom, PANEL_FILL_TOP, PANEL_FILL_BOTTOM);
        guiGraphics.renderOutline(left, top, panelWidth, panelHeight, PANEL_OUTLINE);
        guiGraphics.renderOutline(left + 2, top + 2, panelWidth - 4, panelHeight - 4, PANEL_INNER);

        int titleY = top + 7;
        guiGraphics.drawCenteredString(font, TITLE, screenWidth / 2, titleY, TITLE_COLOR);
        guiGraphics.drawCenteredString(font, stageLabel, screenWidth / 2, titleY + 12, STAGE_COLOR);

        if (detail != null) {
            int detailColor = snapshot.subtext() == null ? TEXT_COLOR : SUBTEXT_COLOR;
            guiGraphics.drawCenteredString(font, detail, screenWidth / 2, titleY + 24, detailColor);
        }
        if (hint != null) {
            guiGraphics.drawCenteredString(font, hint, screenWidth / 2, titleY + 36, SUBTEXT_COLOR);
        }

        int barLeft = left + 14;
        int barTop = bottom - 14;
        int barRight = right - 14;
        int barBottom = barTop + 6;
        int barWidth = barRight - barLeft;
        float visualProgress = snapshot.visualProgress();

        guiGraphics.fill(barLeft, barTop, barRight, barBottom, BAR_BG);
        guiGraphics.renderOutline(barLeft - 1, barTop - 1, barWidth + 2, 8, PANEL_INNER);

        int fillWidth = Math.max(1, Mth.floor(barWidth * visualProgress));
        guiGraphics.fill(barLeft, barTop, barLeft + fillWidth, barBottom, BAR_FILL_SOFT);
        guiGraphics.fill(barLeft, barTop, barLeft + Math.max(1, fillWidth - 1), barBottom - 1, BAR_FILL);

        if (snapshot.hasDeterminateProgress()) {
            guiGraphics.fill(barLeft, barTop, barLeft + Math.max(1, fillWidth - 10), barBottom - 2, BAR_GLOW);
            String percent = Math.round(snapshot.progress() * 100.0F) + "%";
            guiGraphics.drawString(font, percent, right - 14 - font.width(percent), top + 7, PERCENT_COLOR, false);
        } else {
            int segmentWidth = Math.max(24, barWidth / 5);
            int travel = Math.max(1, barWidth - segmentWidth);
            int segmentLeft = barLeft + (int) ((now % 1400L) / 1400.0F * travel);
            int segmentRight = Math.min(barRight, segmentLeft + segmentWidth);
            guiGraphics.fill(segmentLeft, barTop + 1, segmentRight, barBottom - 1, BAR_GLOW);
        }
    }

    private static String buildDetail(ForecastLoadingState.Snapshot snapshot, long now) {
        if (snapshot.subtext() != null) {
            return snapshot.subtext();
        }
        if (snapshot.message() == null || snapshot.message().isBlank()) {
            return null;
        }
        if (snapshot.hasDeterminateProgress()) {
            return snapshot.message();
        }
        return snapshot.message() + DOTS[(int) ((now / 350L) % DOTS.length)];
    }

    private static String buildHint(ForecastLoadingState.Snapshot snapshot) {
        if (snapshot.stage() == ForecastLoadingStage.WAITING_FOR_SERVER) {
            return WAITING_HINT;
        }
        return null;
    }

    private static String fitText(Font font, String text, int maxWidth) {
        if (text == null || font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int contentWidth = Math.max(1, maxWidth - font.width(suffix));
        return font.plainSubstrByWidth(text, contentWidth) + suffix;
    }
}

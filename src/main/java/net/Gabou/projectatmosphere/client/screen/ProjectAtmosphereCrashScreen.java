package net.Gabou.projectatmosphere.client.screen;

import net.Gabou.projectatmosphere.client.crash.ProjectAtmosphereCrashHandler;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class ProjectAtmosphereCrashScreen extends Screen {
    private static final Component TITLE = Component.literal("Project Atmosphere Crash Detected");
    private static final Component MESSAGE = Component.literal("It looks like Project Atmosphere crashed. Please join the Discord server to submit your issue.");

    private final ProjectAtmosphereCrashHandler.CrashContext crashContext;
    private Component statusMessage = Component.empty();

    public ProjectAtmosphereCrashScreen(ProjectAtmosphereCrashHandler.CrashContext crashContext) {
        super(TITLE);
        this.crashContext = crashContext;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(240, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        int baseY = this.height - 86;

        this.addRenderableWidget(Button.builder(Component.literal("Copy Support Summary"), button -> {
            if (this.minecraft != null) {
                this.minecraft.keyboardHandler.setClipboard(this.crashContext.supportSummary());
                this.statusMessage = Component.literal("Support summary copied to clipboard.");
            }
        }).bounds(left, baseY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Open Discord"), button -> {
            Util.getPlatform().openUri(ProjectAtmosphereCrashHandler.getDiscordInviteUrl());
            this.statusMessage = Component.literal("Opened the Discord invite in your browser.");
        }).bounds(left, baseY + 24, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Close Game"), button -> {
            if (this.minecraft != null) {
                this.minecraft.stop();
            }
        }).bounds(left, baseY + 48, buttonWidth, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xFF200D12, 0xFF070A10);

        int contentWidth = Math.min(420, this.width - 48);
        int contentLeft = (this.width - contentWidth) / 2;
        int panelTop = 24;
        int panelBottom = this.height - 16;
        guiGraphics.fill(contentLeft - 12, panelTop - 12, contentLeft + contentWidth + 12, panelBottom, 0xB0141C24);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop, 0xFFF4F4F4);

        int y = panelTop + 24;
        y = drawWrapped(guiGraphics, MESSAGE, contentLeft, y, contentWidth, 0xFFE9D7D7);
        y += 10;
        y = drawField(guiGraphics, "Crash", this.crashContext.crashTitle(), contentLeft, y, contentWidth);
        y = drawField(guiGraphics, "Exception", this.crashContext.exceptionSummary(), contentLeft, y, contentWidth);
        y = drawField(guiGraphics, "PA Frame", this.crashContext.projectAtmosphereFrame(), contentLeft, y, contentWidth);
        drawField(guiGraphics, "Report", this.crashContext.savedReportPath(), contentLeft, y, contentWidth);

        if (!this.statusMessage.getString().isBlank()) {
            guiGraphics.drawCenteredString(this.font, this.statusMessage, this.width / 2, this.height - 104, 0xFFACF59B);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private int drawField(GuiGraphics guiGraphics, String label, String value, int left, int top, int width) {
        guiGraphics.drawString(this.font, label + ":", left, top, 0xFFFFC5C5, false);
        return drawWrapped(guiGraphics, Component.literal(value), left, top + 12, width, 0xFFF4F4F4) + 8;
    }

    private int drawWrapped(GuiGraphics guiGraphics, Component text, int left, int top, int width, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        int y = top;

        for (FormattedCharSequence line : lines) {
            guiGraphics.drawString(this.font, line, left, y, color, false);
            y += 10;
        }

        return y;
    }
}

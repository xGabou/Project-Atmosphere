package net.Gabou.projectatmosphere.client.screen;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.TfcSeasonConflict;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A blocking early-client warning for TFC plus a second season provider. */
@OnlyIn(Dist.CLIENT)
public final class TfcSeasonConflictScreen extends Screen {
    private static final Component TITLE = Component.literal("TFC Seasonal Mod Conflict");

    private final TfcSeasonConflict.Conflict conflict;
    private Component statusMessage = Component.empty();

    private TfcSeasonConflictScreen(TfcSeasonConflict.Conflict conflict) {
        super(TITLE);
        this.conflict = conflict;
    }

    /**
     * Called from the first client ticks, including the title screen. Returning
     * true lets callers avoid starting normal PA client work in this invalid pack.
     */
    public static boolean presentIfNeeded(Minecraft minecraft) {
        Optional<TfcSeasonConflict.Conflict> conflict = TfcSeasonConflict.detectLoadedConflict();
        if (conflict.isEmpty()) {
            return false;
        }

        if (!(minecraft.screen instanceof TfcSeasonConflictScreen)) {
            ProjectAtmosphere.LOGGER.error(
                    "TFC season conflict detected: {}. Blocking the client until the conflicting mod jars are removed.",
                    conflict.get().displayNames());
            minecraft.setScreen(new TfcSeasonConflictScreen(conflict.get()));
        }
        return true;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(250, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        int baseY = this.height - 70;

        this.addRenderableWidget(Button.builder(Component.literal("Open Mods Folder in File Explorer"), button -> {
            if (this.minecraft == null) {
                return;
            }
            Path modsDirectory = this.minecraft.gameDirectory.toPath().resolve("mods");
            Util.getPlatform().openUri(modsDirectory.toUri());
            this.statusMessage = Component.literal("Mods folder opened. Quit Minecraft before deleting the jar file(s).");
        }).bounds(left, baseY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Quit Minecraft"), button -> {
            if (this.minecraft != null) {
                this.minecraft.stop();
            }
        }).bounds(left, baseY + 26, buttonWidth, 20).build());
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
        this.renderBackground(guiGraphics);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xFF21100C, 0xFF080A10);

        int contentWidth = Math.min(460, this.width - 48);
        int contentLeft = (this.width - contentWidth) / 2;
        int panelTop = Math.max(20, this.height / 2 - 112);
        int panelBottom = this.height - 12;
        guiGraphics.fill(contentLeft - 12, panelTop - 12, contentLeft + contentWidth + 12, panelBottom, 0xC01B1D24);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop, 0xFFFFD6B0);

        int y = panelTop + 24;
        y = drawWrapped(guiGraphics, Component.literal("Project Atmosphere detected TerraFirmaCraft together with: "
                + this.conflict.displayNames() + "."), contentLeft, y, contentWidth, 0xFFF4F4F4);
        y += 8;
        y = drawWrapped(guiGraphics, Component.literal("TFC already provides seasonal behavior. Remove the listed seasonal mod jar file(s) manually from this instance's mods folder."), contentLeft, y, contentWidth, 0xFFFFD6B0);
        y += 8;

        if (this.conflict.hasSereneSeasons()) {
            y = drawWrapped(guiGraphics, Component.literal("Delete the Serene Seasons jar in File Explorer. Do not disable it through CurseForge or Modrinth: those launchers can disable Project Atmosphere instead."), contentLeft, y, contentWidth, 0xFFFFA7A7);
        } else {
            y = drawWrapped(guiGraphics, Component.literal("Do not disable the season mod through CurseForge or Modrinth. Use File Explorer to delete its jar so Project Atmosphere remains enabled."), contentLeft, y, contentWidth, 0xFFFFA7A7);
        }
        y += 8;
        drawWrapped(guiGraphics, Component.literal("Open the folder, quit Minecraft, delete the listed jar file(s), then restart the instance."), contentLeft, y, contentWidth, 0xFFE9E9E9);

        if (!this.statusMessage.getString().isBlank()) {
            guiGraphics.drawCenteredString(this.font, this.statusMessage, this.width / 2, this.height - 82, 0xFFACF59B);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
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

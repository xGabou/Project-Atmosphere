package net.Gabou.projectatmosphere.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/**
 * Éditeur in-game du shader live des nuages.
 * Cette vue est attachée au menu de configuration principal.
 */
public final class CloudShaderEditorScreen extends Screen {

    private final Screen parent;
    private MultiLineEditBox shaderBox;
    private Button saveButton;
    private Button reloadButton;
    private Button revertButton;
    private Component statusMessage = Component.literal("Prêt.");
    private String loadedText = "";
    private boolean dirty;

    public CloudShaderEditorScreen(Screen parent) {
        super(Component.literal("Cloud Shader Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int margin = 20;
        int top = 36;
        int bottom = this.height - 44;
        int contentWidth = Math.max(200, this.width - margin * 2);
        int editorHeight = Math.max(160, bottom - top);

        String initialText = this.dirty ? this.loadedText : loadShaderText();
        this.loadedText = initialText;
        this.dirty = false;
        if (this.statusMessage.getString().equals("Prêt.")) {
            this.statusMessage = Component.literal("Fichier: " + CloudShaderSourceManager.describePrimaryTarget());
        }

        this.shaderBox = new MultiLineEditBox(
                this.font,
                margin,
                top,
                contentWidth,
                editorHeight,
                Component.literal("Shader source"),
                Component.literal("Shader source")
        );
        this.shaderBox.setCharacterLimit(200000);
        this.setShaderText(initialText);
        this.shaderBox.setValueListener(value -> {
            if (this.suppressDirtyUpdate) {
                return;
            }
            this.loadedText = value;
            this.dirty = true;
            this.statusMessage = Component.literal("Modifications non sauvegardées.");
            this.updateButtonState();
        });
        this.addRenderableWidget(this.shaderBox);

        int buttonY = this.height - 28;
        int buttonWidth = 74;
        int spacing = 6;
        int totalWidth = buttonWidth * 4 + spacing * 3;
        int startX = (this.width - totalWidth) / 2;

        this.saveButton = this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.saveShader())
                .bounds(startX, buttonY, buttonWidth, 20)
                .build());
        this.reloadButton = this.addRenderableWidget(Button.builder(Component.literal("Reload"), button -> this.reloadShader())
                .bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, 20)
                .build());
        this.revertButton = this.addRenderableWidget(Button.builder(Component.literal("Revert"), button -> this.revertShader())
                .bounds(startX + (buttonWidth + spacing) * 2, buttonY, buttonWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.onClose())
                .bounds(startX + (buttonWidth + spacing) * 3, buttonY, buttonWidth, 20)
                .build());

        this.setInitialFocus(this.shaderBox);
        this.updateButtonState();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int panelX = 12;
        int panelY = 14;
        int panelW = this.width - 24;
        int panelH = this.height - 28;

        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC0101010);
        guiGraphics.drawString(this.font, "Cloud Shader Editor", panelX + 8, panelY + 8, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "Editing: cloud_volume.fsh", panelX + 8, panelY + 20, 0xB0FFFFFF, false);
        guiGraphics.drawString(this.font, this.statusMessage, panelX + 8, panelY + 32, 0xB0FFFFFF, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String currentText = this.shaderBox == null ? this.loadedText : this.shaderBox.getValue();
        super.resize(minecraft, width, height);
        this.loadedText = currentText;
    }

    private String loadShaderText() {
        try {
            return CloudShaderSourceManager.readShaderSource();
        } catch (IOException exception) {
            this.statusMessage = Component.literal("Lecture impossible: " + exception.getMessage());
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Impossible de charger le shader live.", exception);
            return "";
        }
    }

    private void saveShader() {
        try {
            CloudShaderSourceManager.SaveResult result = CloudShaderSourceManager.saveShaderSource(this.shaderBox.getValue());
            this.dirty = false;
            this.statusMessage = Component.literal("Sauvegardé: " + result.writtenPaths().size() + "/" + result.allTargets().size() + " cible(s).");
            Minecraft minecraft = Minecraft.getInstance();
            CloudShaderSourceManager.reloadClientResources().whenComplete((ignored, throwable) -> minecraft.execute(() -> {
                if (throwable != null) {
                    this.statusMessage = Component.literal("Sauvegarde OK, rechargement en échec.");
                    ProjectAtmosphere.LOGGER.warn("[Atmosphere] Rechargement des ressources client échoué.", throwable);
                } else {
                    this.statusMessage = Component.literal("Sauvegardé et ressources rechargées.");
                }
                this.updateButtonState();
            }));
            this.updateButtonState();
        } catch (IOException exception) {
            this.statusMessage = Component.literal("Échec de sauvegarde: " + exception.getMessage());
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Échec de sauvegarde du shader live.", exception);
        }
    }

    private void reloadShader() {
        this.loadedText = loadShaderText();
        this.setShaderText(this.loadedText);
        this.dirty = false;
        this.statusMessage = Component.literal("Shader rechargé depuis le disque.");
        this.updateButtonState();
    }

    private void revertShader() {
        this.loadedText = loadShaderText();
        this.setShaderText(this.loadedText);
        this.dirty = false;
        this.statusMessage = Component.literal("Modifications annulées.");
        this.updateButtonState();
    }

    private void updateButtonState() {
        boolean editable = CloudShaderSourceManager.hasEditableTargets();
        if (this.saveButton != null) {
            this.saveButton.active = editable && this.dirty;
        }
        if (this.reloadButton != null) {
            this.reloadButton.active = true;
        }
        if (this.revertButton != null) {
            this.revertButton.active = this.dirty;
        }
    }

    private boolean suppressDirtyUpdate;

    private void setShaderText(String text) {
        this.suppressDirtyUpdate = true;
        try {
            this.shaderBox.setValue(text);
        } finally {
            this.suppressDirtyUpdate = false;
        }
    }
}

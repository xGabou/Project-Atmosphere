package net.Gabou.projectatmosphere.client.screen;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.config.AtmoConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Petit menu PA intégré au menu Options de Minecraft.
 * Il expose seulement les réglages de base et laisse les écrans avancés derrière des boutons secondaires.
 */
public final class ProjectAtmosphereQuickOptionsScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 354;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 10;
    private static final int PANEL_PADDING = 14;

    private final Screen parent;
    private AtmoCommonConfig.CloudMode cloudMode;
    private AtmoCommonConfig.CloudRaymarchQuality cloudRaymarchQuality;
    private boolean fogEnabled;
    private boolean eventsEnabled;
    private int nativeCloudSpawnHeight;
    private double forecastDeviationMultiplier;

    private Button cloudModeButton;
    private Button cloudQualityButton;
    private Button fogButton;
    private Button eventsButton;
    private EditBox nativeCloudSpawnHeightBox;
    private EditBox forecastDeviationBox;

    public ProjectAtmosphereQuickOptionsScreen(Screen parent) {
        super(Component.literal("Project Atmosphere"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.cloudMode = AtmoCommonConfig.CLOUD_MODE.get();
        this.cloudRaymarchQuality = AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.get();
        this.fogEnabled = AtmoCommonConfig.FOG_ENABLED.get();
        this.eventsEnabled = AtmoCommonConfig.EVENTS_ENABLED.get();
        this.nativeCloudSpawnHeight = AtmoCommonConfig.NATIVE_CLOUD_SPAWN_HEIGHT.get();
        this.forecastDeviationMultiplier = AtmoCommonConfig.FORECAST_DEVIATION_MULTIPLIER.get();

        int panelWidth = this.getPanelWidth();
        int left = this.getPanelLeft() + PANEL_PADDING;
        int top = this.getPanelTop() + 48;
        int rowWidth = panelWidth - PANEL_PADDING * 2;
        int halfWidth = (rowWidth - 8) / 2;

        this.cloudModeButton = this.addRenderableWidget(Button.builder(this.cloudModeLabel(), button -> {
            this.cloudMode = this.cloudMode.next();
            button.setMessage(this.cloudModeLabel());
        }).bounds(left, top, rowWidth, ROW_HEIGHT).build());

        this.cloudQualityButton = this.addRenderableWidget(Button.builder(this.cloudQualityLabel(), button -> {
            this.cloudRaymarchQuality = this.cloudRaymarchQuality.next();
            button.setMessage(this.cloudQualityLabel());
        }).bounds(left, top + 30, rowWidth, ROW_HEIGHT).build());

        this.fogButton = this.addRenderableWidget(Button.builder(this.toggleLabel("Fog", this.fogEnabled), button -> {
            this.fogEnabled = !this.fogEnabled;
            button.setMessage(this.toggleLabel("Fog", this.fogEnabled));
        }).bounds(left, top + 60, rowWidth, ROW_HEIGHT).build());

        this.eventsButton = this.addRenderableWidget(Button.builder(this.toggleLabel("Events", this.eventsEnabled), button -> {
            this.eventsEnabled = !this.eventsEnabled;
            button.setMessage(this.toggleLabel("Events", this.eventsEnabled));
        }).bounds(left, top + 90, rowWidth, ROW_HEIGHT).build());

        this.nativeCloudSpawnHeightBox = new EditBox(this.font, left, top + 132, rowWidth, ROW_HEIGHT, Component.literal("Native cloud height"));
        this.nativeCloudSpawnHeightBox.setValue(Integer.toString(this.nativeCloudSpawnHeight));
        this.nativeCloudSpawnHeightBox.setMaxLength(8);
        this.addRenderableWidget(this.nativeCloudSpawnHeightBox);

        this.forecastDeviationBox = new EditBox(this.font, left, top + 180, rowWidth, ROW_HEIGHT, Component.literal("Forecast deviation"));
        this.forecastDeviationBox.setValue(Double.toString(this.forecastDeviationMultiplier));
        this.forecastDeviationBox.setMaxLength(12);
        this.addRenderableWidget(this.forecastDeviationBox);

        this.addRenderableWidget(Button.builder(Component.literal("Shader"), button ->
                Minecraft.getInstance().setScreen(new CloudShaderEditorScreen(this)))
                .bounds(left, top + 226, halfWidth, ROW_HEIGHT)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Advanced"), button ->
                Minecraft.getInstance().setScreen(new AtmoConfigScreen(this)))
                .bounds(left + halfWidth + 8, top + 226, halfWidth, ROW_HEIGHT)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.saveAndClose())
                .bounds(left, this.getPanelTop() + PANEL_HEIGHT - PANEL_PADDING - ROW_HEIGHT, rowWidth, ROW_HEIGHT)
                .build());

        this.setInitialFocus(this.forecastDeviationBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int panelWidth = this.getPanelWidth();
        int left = this.getPanelLeft();
        int top = this.getPanelTop();

        guiGraphics.fill(left, top, left + panelWidth, top + PANEL_HEIGHT, 0xD0101218);
        guiGraphics.drawCenteredString(this.font, "Project Atmosphere", this.width / 2, top + 10, 0xFFE5F2FF);
        guiGraphics.drawCenteredString(this.font, "Basic cloud and weather controls", this.width / 2, top + 22, 0xFF9FB7C8);
        guiGraphics.drawString(this.font, "Native cloud height", left + PANEL_PADDING, top + 164, 0xFFE5F2FF, false);
        guiGraphics.drawString(this.font, "Default: 256", left + PANEL_PADDING, top + 204, 0xFF9FB7C8, false);
        guiGraphics.drawString(this.font, "Forecast deviation", left + PANEL_PADDING, top + 216, 0xFFE5F2FF, false);
        guiGraphics.drawString(this.font, "Forecast swing multiplier", left + PANEL_PADDING, top + 254, 0xFF9FB7C8, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private Component cloudModeLabel() {
        return Component.literal("Cloud mode: " + this.cloudMode.name());
    }

    private Component cloudQualityLabel() {
        return Component.literal("Cloud quality: " + this.cloudRaymarchQuality.name() + " (" + this.cloudRaymarchQuality.getRaymarchSteps() + " steps)");
    }

    private Component toggleLabel(String label, boolean value) {
        return Component.literal(label + ": " + (value ? "ON" : "OFF"));
    }

    private int getPanelWidth() {
        return Math.min(PANEL_WIDTH, this.width - 32);
    }

    private int getPanelLeft() {
        return (this.width - this.getPanelWidth()) / 2;
    }

    private int getPanelTop() {
        return Math.max(12, (this.height - PANEL_HEIGHT) / 2);
    }

    private void saveAndClose() {
        this.nativeCloudSpawnHeight = Mth.clamp(this.parseInt(this.nativeCloudSpawnHeightBox, this.nativeCloudSpawnHeight), -2048, 4096);
        this.forecastDeviationMultiplier = Mth.clamp(this.parseDouble(this.forecastDeviationBox, this.forecastDeviationMultiplier), 0.0D, 3.0D);

        AtmoCommonConfig.CLOUD_MODE.set(this.cloudMode);
        AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(this.cloudRaymarchQuality);
        AtmoCommonConfig.NATIVE_CLOUD_SPAWN_HEIGHT.set(this.nativeCloudSpawnHeight);
        AtmoCommonConfig.FOG_ENABLED.set(this.fogEnabled);
        AtmoCommonConfig.EVENTS_ENABLED.set(this.eventsEnabled);
        AtmoCommonConfig.FORECAST_DEVIATION_MULTIPLIER.set(this.forecastDeviationMultiplier);

        try {
            saveCommonConfigForMod(ProjectAtmosphere.MODID);
        } catch (Exception ignored) {
        }

        Minecraft.getInstance().setScreen(this.parent);
    }

    private int parseInt(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private double parseDouble(EditBox box, double fallback) {
        try {
            return Double.parseDouble(box.getValue());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * Sauvegarde la configuration COMMON du mod courant.
     *
     * @param modId identifiant du mod à sauvegarder
     */
    private static void saveCommonConfigForMod(String modId) {
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
    }
}

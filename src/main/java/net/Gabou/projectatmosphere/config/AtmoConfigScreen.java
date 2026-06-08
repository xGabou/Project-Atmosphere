package net.Gabou.projectatmosphere.config;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.screen.CloudShaderEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Ecran de configuration interne de Project Atmosphere.
 */
public class AtmoConfigScreen extends Screen {
    private final Screen parent;

    private boolean forceSharedExecutor;
    private boolean displayUnitsImperial;
    private boolean enableTornadoes;
    private boolean enableTornadoDestruction;
    private boolean enableStormDebris;
    private boolean fogEnabled;
    private int maxStormDebrisPerChunk;
    private boolean autoRepairGlass;
    private boolean damageGlassOnTornado;
    private boolean enableHurricaneDestruction;
    private double hurricaneDestructionStrength;
    private boolean hurricaneDropBrokenBlocks;
    private boolean hurricaneDamageTrees;
    private double tornadoCheckIntervalSec;
    private double tornadoBaseSpawnRadiusM;
    private double tornadoMinTempContrastC;
    private double tornadoHumidityMinPercent;
    private double tornadoPressureGradientGain;
    private double tornadoPressureGradientCap;
    private double tornadoShearMinSpeedDiffMps;
    private double tornadoShearMinDirDiffDeg;
    private double tornadoStormMultiplier;
    private double tornadoRiskMinToConsider;
    private double tornadoBaseTriggerChance;
    private double tornadoLapseRateCPer100m;
    private double tornadoAloftDeltaHM;
    private double tornadoIntensityMin;
    private double tornadoIntensityMax;
    private int tornadoCellCooldownMinutes;
    private boolean tornadoAllowLegacyFallback;
    private boolean tornadoDebugLogging;
    private double tornadoRenderQuality;
    private double tornadoRenderDownsample;
    private double windBaseRetargetSec;
    private double windDirRetargetSec;
    private double windGustMeanSec;
    private double windGustDecayMps;
    private double windStormGustMult;
    private double windPushThresholdMps;
    private double windPlayerPushScale;
    private double windEntityPushScale;
    private double fogHumidityStartPercent;
    private double fogHumidityFullPercent;
    private double fogWetBiomeBaseStrength;
    private double fogRainBoost;
    private double fogFarDistance;
    private double fogColorBlend;
    private double stormBoostMultiplier;
    private boolean debugMode;

    private EditBox maxDebrisBox;
    private int cloudRenderDistance;
    private EditBox cloudDistanceBox;
    private EditBox tornadoCheckIntervalBox;
    private EditBox tornadoBaseSpawnRadiusBox;
    private EditBox tornadoMinTempContrastBox;
    private EditBox tornadoHumidityMinBox;
    private EditBox tornadoPressureGradientGainBox;
    private EditBox tornadoPressureGradientCapBox;
    private EditBox tornadoShearMinSpeedDiffBox;
    private EditBox tornadoShearMinDirDiffBox;
    private EditBox tornadoStormMultiplierBox;
    private EditBox tornadoRiskMinBox;
    private EditBox tornadoBaseTriggerChanceBox;
    private EditBox tornadoLapseRateBox;
    private EditBox tornadoAloftDeltaBox;
    private EditBox tornadoIntensityMinBox;
    private EditBox tornadoIntensityMaxBox;
    private EditBox tornadoCellCooldownBox;
    private EditBox tornadoRenderQualityBox;
    private EditBox tornadoRenderDownsampleBox;
    private EditBox hurricaneDestructionStrengthBox;
    private EditBox fogHumidityStartBox;
    private EditBox fogHumidityFullBox;
    private EditBox fogWetBiomeStrengthBox;
    private EditBox fogRainBoostBox;
    private EditBox fogFarDistanceBox;
    private EditBox fogColorBlendBox;
    private EditBox windBaseRetargetBox;
    private EditBox windDirRetargetBox;
    private EditBox windGustMeanBox;
    private EditBox windGustDecayBox;
    private EditBox windStormGustMultBox;
    private EditBox windPushThresholdBox;
    private EditBox windPlayerPushScaleBox;
    private EditBox windEntityPushScaleBox;
    private EditBox stormBoostMultiplierBox;
    private EditBox debugModeBox;

    private final List<AbstractWidget> configWidgets = new ArrayList<>();
    private final List<Integer> widgetBaseY = new ArrayList<>();

    private static class Title {
        final String text;
        final int y;

        Title(String text, int y) {
            this.text = text;
            this.y = y;
        }
    }

    private static class Label {
        final String text;
        final int x;
        final int y;

        Label(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }

    private final List<Title> titles = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;

    public AtmoConfigScreen(Screen parent) {
        super(Component.literal("Project Atmosphere Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        configWidgets.clear();
        widgetBaseY.clear();
        titles.clear();
        labels.clear();

        this.debugMode = AtmoCommonConfig.DEBUG_MODE.get();
        this.forceSharedExecutor = AtmoCommonConfig.FORCE_SHARED_EXECUTOR.get();
        this.stormBoostMultiplier = AtmoCommonConfig.STORM_SEVERITY_BOOSTER.get();
        this.displayUnitsImperial = AtmoCommonConfig.DISPLAY_UNITS_IMPERIAL.get();
        this.enableTornadoes = AtmoCommonConfig.ENABLE_TORNADOES.get();
        this.enableTornadoDestruction = AtmoCommonConfig.ENABLE_TORNADO_DESTRUCTION.get();
        this.enableStormDebris = AtmoCommonConfig.ENABLE_STORM_DEBRIS.get();
        this.fogEnabled = AtmoCommonConfig.FOG_ENABLED.get();
        this.maxStormDebrisPerChunk = AtmoCommonConfig.MAX_STORM_DEBRIS_PER_CHUNK.get();
        this.autoRepairGlass = AtmoCommonConfig.AUTO_REPAIR_GLASS.get();
        this.damageGlassOnTornado = AtmoCommonConfig.DAMAGE_GLASS_ON_TORNADO.get();
        this.enableHurricaneDestruction = AtmoCommonConfig.ENABLE_HURRICANE_DESTRUCTION.get();
        this.hurricaneDestructionStrength = AtmoCommonConfig.HURRICANE_DESTRUCTION_STRENGTH.get();
        this.hurricaneDropBrokenBlocks = AtmoCommonConfig.HURRICANE_DROP_BROKEN_BLOCKS.get();
        this.hurricaneDamageTrees = AtmoCommonConfig.HURRICANE_DAMAGE_TREES.get();
        this.cloudRenderDistance = AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get();
        this.tornadoCheckIntervalSec = AtmoCommonConfig.TORNADO_CHECK_INTERVAL_SEC.get();
        this.tornadoBaseSpawnRadiusM = AtmoCommonConfig.TORNADO_BASE_SPAWN_RADIUS_M.get();
        this.tornadoMinTempContrastC = AtmoCommonConfig.TORNADO_MIN_TEMP_CONTRAST_C.get();
        this.tornadoHumidityMinPercent = AtmoCommonConfig.TORNADO_HUMIDITY_MIN_PERCENT.get();
        this.tornadoPressureGradientGain = AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_GAIN.get();
        this.tornadoPressureGradientCap = AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_CAP.get();
        this.tornadoShearMinSpeedDiffMps = AtmoCommonConfig.TORNADO_SHEAR_MIN_SPEED_DIFF_MPS.get();
        this.tornadoShearMinDirDiffDeg = AtmoCommonConfig.TORNADO_SHEAR_MIN_DIR_DIFF_DEG.get();
        this.tornadoStormMultiplier = AtmoCommonConfig.TORNADO_STORM_MULTIPLIER.get();
        this.tornadoRiskMinToConsider = AtmoCommonConfig.TORNADO_RISK_MIN_TO_CONSIDER.get();
        this.tornadoBaseTriggerChance = AtmoCommonConfig.TORNADO_BASE_TRIGGER_CHANCE.get();
        this.tornadoLapseRateCPer100m = AtmoCommonConfig.TORNADO_LAPSE_RATE_C_PER_100M.get();
        this.tornadoAloftDeltaHM = AtmoCommonConfig.TORNADO_ALOFT_DELTA_H_M.get();
        this.tornadoIntensityMin = AtmoCommonConfig.TORNADO_INTENSITY_MIN.get();
        this.tornadoIntensityMax = AtmoCommonConfig.TORNADO_INTENSITY_MAX.get();
        this.tornadoCellCooldownMinutes = AtmoCommonConfig.TORNADO_CELL_COOLDOWN_MINUTES.get();
        this.tornadoAllowLegacyFallback = AtmoCommonConfig.TORNADO_ALLOW_LEGACY_FALLBACK.get();
        this.tornadoDebugLogging = AtmoCommonConfig.TORNADO_DEBUG_LOGGING.get();
        this.tornadoRenderQuality = AtmoCommonConfig.TORNADO_RENDER_QUALITY.get();
        this.tornadoRenderDownsample = AtmoCommonConfig.TORNADO_RENDER_DOWNSAMPLE.get();
        this.windBaseRetargetSec = AtmoCommonConfig.WIND_BASE_RETARGET_SEC.get();
        this.windDirRetargetSec = AtmoCommonConfig.WIND_DIR_RETARGET_SEC.get();
        this.windGustMeanSec = AtmoCommonConfig.WIND_GUST_MEAN_SEC.get();
        this.windGustDecayMps = AtmoCommonConfig.WIND_GUST_DECAY_MPS.get();
        this.windStormGustMult = AtmoCommonConfig.WIND_STORM_GUST_MULT.get();
        this.windPushThresholdMps = AtmoCommonConfig.WIND_PUSH_THRESHOLD_MPS.get();
        this.windPlayerPushScale = AtmoCommonConfig.WIND_PLAYER_PUSH_SCALE.get();
        this.windEntityPushScale = AtmoCommonConfig.WIND_ENTITY_PUSH_SCALE.get();
        this.fogHumidityStartPercent = AtmoCommonConfig.FOG_HUMIDITY_START_PERCENT.get();
        this.fogHumidityFullPercent = AtmoCommonConfig.FOG_HUMIDITY_FULL_PERCENT.get();
        this.fogWetBiomeBaseStrength = AtmoCommonConfig.FOG_WET_BIOME_BASE_STRENGTH.get();
        this.fogRainBoost = AtmoCommonConfig.FOG_RAIN_BOOST.get();
        this.fogFarDistance = AtmoCommonConfig.FOG_FAR_DISTANCE.get();
        this.fogColorBlend = AtmoCommonConfig.FOG_COLOR_BLEND.get();

        addRenderableWidget(Button.builder(Component.literal("Shader"), button -> Minecraft.getInstance().setScreen(new CloudShaderEditorScreen(this)))
                .bounds(this.width - 88, 8, 80, 20)
                .build());

        int center = this.width / 2;
        int y = 40;

        addTitle("Performance", y);
        y += 18;
        addConfigWidget(Button.builder(toggleLabel("Force Shared Executor", forceSharedExecutor), b -> {
            forceSharedExecutor = !forceSharedExecutor;
            b.setMessage(toggleLabel("Force Shared Executor", forceSharedExecutor));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        tornadoRenderQualityBox = addNumberField(center, y, "Tornado Render Quality", Double.toString(tornadoRenderQuality));
        y += 34;
        tornadoRenderDownsampleBox = addNumberField(center, y, "Tornado Render Downsample", Double.toString(tornadoRenderDownsample));
        y += 34;

        addTitle("Display", y);
        y += 18;
        addConfigWidget(Button.builder(toggleLabel("Imperial Units", displayUnitsImperial), b -> {
            displayUnitsImperial = !displayUnitsImperial;
            b.setMessage(toggleLabel("Imperial Units", displayUnitsImperial));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;

        addTitle("Fog", y);
        y += 18;
        addConfigWidget(Button.builder(toggleLabel("Dynamic Fog", fogEnabled), b -> {
            fogEnabled = !fogEnabled;
            b.setMessage(toggleLabel("Dynamic Fog", fogEnabled));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        fogHumidityStartBox = addNumberField(center, y, "Humidity Start Percent", Double.toString(fogHumidityStartPercent));
        y += 34;
        fogHumidityFullBox = addNumberField(center, y, "Humidity Full Percent", Double.toString(fogHumidityFullPercent));
        y += 34;
        fogWetBiomeStrengthBox = addNumberField(center, y, "Wet Biome Strength", Double.toString(fogWetBiomeBaseStrength));
        y += 34;
        fogRainBoostBox = addNumberField(center, y, "Rain Boost", Double.toString(fogRainBoost));
        y += 34;
        fogFarDistanceBox = addNumberField(center, y, "Fog Far Distance", Double.toString(fogFarDistance));
        y += 34;
        fogColorBlendBox = addNumberField(center, y, "Fog Color Blend", Double.toString(fogColorBlend));
        y += 34;

        addTitle("Storms", y);
        y += 18;
        addConfigWidget(Button.builder(toggleLabel("Tornadoes", enableTornadoes), b -> {
            enableTornadoes = !enableTornadoes;
            b.setMessage(toggleLabel("Tornadoes", enableTornadoes));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        addConfigWidget(Button.builder(toggleLabel("Tornado Destruction", enableTornadoDestruction), b -> {
            enableTornadoDestruction = !enableTornadoDestruction;
            b.setMessage(toggleLabel("Tornado Destruction", enableTornadoDestruction));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        addConfigWidget(Button.builder(toggleLabel("Storm Debris", enableStormDebris), b -> {
            enableStormDebris = !enableStormDebris;
            b.setMessage(toggleLabel("Storm Debris", enableStormDebris));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;

        this.cloudDistanceBox = addNumberField(center, y, "Cloud Render Distance", Integer.toString(cloudRenderDistance));
        y += 34;

        this.stormBoostMultiplierBox = addNumberField(center, y, "Storm Severity Booster", Double.toString(stormBoostMultiplier));
        y += 34;

        maxDebrisBox = addNumberField(center, y, "Max Storm Debris Per Chunk", Integer.toString(maxStormDebrisPerChunk));
        y += 34;
        addConfigWidget(Button.builder(toggleLabel("Auto Repair Glass", autoRepairGlass), b -> {
            autoRepairGlass = !autoRepairGlass;
            b.setMessage(toggleLabel("Auto Repair Glass", autoRepairGlass));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        addConfigWidget(Button.builder(toggleLabel("Damage Glass On Tornado", damageGlassOnTornado), b -> {
            damageGlassOnTornado = !damageGlassOnTornado;
            b.setMessage(toggleLabel("Damage Glass On Tornado", damageGlassOnTornado));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        addConfigWidget(Button.builder(toggleLabel("Hurricane Destruction", enableHurricaneDestruction), b -> {
            enableHurricaneDestruction = !enableHurricaneDestruction;
            b.setMessage(toggleLabel("Hurricane Destruction", enableHurricaneDestruction));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        hurricaneDestructionStrengthBox = addNumberField(center, y, "Hurricane Destruction Strength", Double.toString(hurricaneDestructionStrength));
        y += 34;
        addConfigWidget(Button.builder(toggleLabel("Drop Hurricane Blocks", hurricaneDropBrokenBlocks), b -> {
            hurricaneDropBrokenBlocks = !hurricaneDropBrokenBlocks;
            b.setMessage(toggleLabel("Drop Hurricane Blocks", hurricaneDropBrokenBlocks));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        addConfigWidget(Button.builder(toggleLabel("Hurricane Tree Damage", hurricaneDamageTrees), b -> {
            hurricaneDamageTrees = !hurricaneDamageTrees;
            b.setMessage(toggleLabel("Hurricane Tree Damage", hurricaneDamageTrees));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;

        addTitle("Tornado", y);
        y += 18;
        addConfigWidget(Button.builder(toggleLabel("Legacy Tornado Fallback", tornadoAllowLegacyFallback), b -> {
            tornadoAllowLegacyFallback = !tornadoAllowLegacyFallback;
            b.setMessage(toggleLabel("Legacy Tornado Fallback", tornadoAllowLegacyFallback));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        addConfigWidget(Button.builder(toggleLabel("Tornado Debug Logging", tornadoDebugLogging), b -> {
            tornadoDebugLogging = !tornadoDebugLogging;
            b.setMessage(toggleLabel("Tornado Debug Logging", tornadoDebugLogging));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;
        tornadoCheckIntervalBox = addNumberField(center, y, "Check Interval Sec", Double.toString(tornadoCheckIntervalSec));
        y += 34;
        tornadoBaseSpawnRadiusBox = addNumberField(center, y, "Base Spawn Radius M", Double.toString(tornadoBaseSpawnRadiusM));
        y += 34;
        tornadoMinTempContrastBox = addNumberField(center, y, "Min Temp Contrast C", Double.toString(tornadoMinTempContrastC));
        y += 34;
        tornadoHumidityMinBox = addNumberField(center, y, "Humidity Min Percent", Double.toString(tornadoHumidityMinPercent));
        y += 34;
        tornadoPressureGradientGainBox = addNumberField(center, y, "Pressure Gradient Gain", Double.toString(tornadoPressureGradientGain));
        y += 34;
        tornadoPressureGradientCapBox = addNumberField(center, y, "Pressure Gradient Cap", Double.toString(tornadoPressureGradientCap));
        y += 34;
        tornadoShearMinSpeedDiffBox = addNumberField(center, y, "Shear Min Speed Diff Mps", Double.toString(tornadoShearMinSpeedDiffMps));
        y += 34;
        tornadoShearMinDirDiffBox = addNumberField(center, y, "Shear Min Dir Diff Deg", Double.toString(tornadoShearMinDirDiffDeg));
        y += 34;
        tornadoStormMultiplierBox = addNumberField(center, y, "Storm Multiplier", Double.toString(tornadoStormMultiplier));
        y += 34;
        tornadoRiskMinBox = addNumberField(center, y, "Risk Min To Consider", Double.toString(tornadoRiskMinToConsider));
        y += 34;
        tornadoBaseTriggerChanceBox = addNumberField(center, y, "Base Trigger Chance", Double.toString(tornadoBaseTriggerChance));
        y += 34;
        tornadoLapseRateBox = addNumberField(center, y, "Lapse Rate C Per 100m", Double.toString(tornadoLapseRateCPer100m));
        y += 34;
        tornadoAloftDeltaBox = addNumberField(center, y, "Aloft Delta H M", Double.toString(tornadoAloftDeltaHM));
        y += 34;
        tornadoIntensityMinBox = addNumberField(center, y, "Intensity Min", Double.toString(tornadoIntensityMin));
        y += 34;
        tornadoIntensityMaxBox = addNumberField(center, y, "Intensity Max", Double.toString(tornadoIntensityMax));
        y += 34;
        tornadoCellCooldownBox = addNumberField(center, y, "Cell Cooldown Minutes", Integer.toString(tornadoCellCooldownMinutes));
        y += 34;

        addTitle("Wind", y);
        y += 18;
        windBaseRetargetBox = addNumberField(center, y, "Base Retarget Sec", Double.toString(windBaseRetargetSec));
        y += 34;
        windDirRetargetBox = addNumberField(center, y, "Dir Retarget Sec", Double.toString(windDirRetargetSec));
        y += 34;
        windGustMeanBox = addNumberField(center, y, "Gust Mean Sec", Double.toString(windGustMeanSec));
        y += 34;
        windGustDecayBox = addNumberField(center, y, "Gust Decay Mps", Double.toString(windGustDecayMps));
        y += 34;
        windStormGustMultBox = addNumberField(center, y, "Storm Gust Mult", Double.toString(windStormGustMult));
        y += 34;
        windPushThresholdBox = addNumberField(center, y, "Push Threshold Mps", Double.toString(windPushThresholdMps));
        y += 34;
        windPlayerPushScaleBox = addNumberField(center, y, "Player Push Scale", Double.toString(windPlayerPushScale));
        y += 34;
        windEntityPushScaleBox = addNumberField(center, y, "Entity Push Scale", Double.toString(windEntityPushScale));
        y += 34;

        addTitle("Debug", y);
        y += 18;
        addConfigWidget(Button.builder(toggleLabel("Debug mode", debugMode), b -> {
            debugMode = !debugMode;
            b.setMessage(toggleLabel("Debug mode", debugMode));
        }).bounds(center - 100, y, 200, 20).build(), y);
        y += 32;

        // Compute scroll range based on the visible viewport between contentTop and contentBottom
        int contentTop = 40;            // Start of scrollable content
        int contentBottom = this.height - 50; // Leave room for the Done button
        int viewportHeight = Math.max(0, contentBottom - contentTop);
        maxScroll = Math.max(0, (y - contentTop) - viewportHeight);
        scrollOffset = 0;
        updateWidgetPositions();

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            saveChanges();
            Minecraft.getInstance().setScreen(parent);
        }).bounds(center - 100, this.height - 27, 200, 20).build());
    }

    private void addConfigWidget(AbstractWidget widget, int baseY) {
        configWidgets.add(widget);
        widgetBaseY.add(baseY);
        addRenderableWidget(widget);
    }

    private EditBox addNumberField(int center, int y, String label, String value) {
        EditBox box = new EditBox(this.font, center - 100, y, 200, 20, Component.literal(label));
        box.setValue(value);
        addConfigWidget(box, y);
        labels.add(new Label(label, center - 100, y - 10));
        return box;
    }

    private void addTitle(String text, int y) {
        titles.add(new Title(text, y));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int panelW = 240;
        int panelX = (this.width - panelW) / 2;
        int contentTop = 40;
        int headerTop = 30;
        int contentBottom = this.height - 50; // stop above the Done button for scrollable area
        int panelBottom = this.height - 20;   // extend panel background to cover the footer region

        // Panel background now extends under the footer so content never looks outside it
        g.fill(panelX - 4, headerTop - 4, panelX + panelW + 4, panelBottom, -1442840576);
        g.drawString(this.font, "Project Atmosphere Config", panelX + 6, headerTop - 14, 0xFFFFFF, false);

        // Clip only the custom drawn titles/labels so they don't render under the Done button
        g.enableScissor(panelX - 4, contentTop - 4, panelX + panelW + 4, contentBottom);

        for (Title t : titles) {
            g.drawString(this.font, t.text, panelX + 6, t.y - scrollOffset, 0xFFFFFF, false);
        }
        for (Label l : labels) {
            g.drawString(this.font, l.text, l.x, l.y - scrollOffset, 0xFFFFFF, false);
        }

        g.disableScissor();

        // Render widgets (including the Done button) outside of scissor so the footer is never clipped
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) (delta * 20), 0, maxScroll);
            updateWidgetPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void updateWidgetPositions() {
        int contentTop = 40;
        int contentBottom = this.height - 50;
        for (int i = 0; i < configWidgets.size(); i++) {
            AbstractWidget w = configWidgets.get(i);
            int y = widgetBaseY.get(i) - scrollOffset;
            w.setY(y);
            // Le widget doit rester entierement dans la zone visible pour eviter les debordements.
            boolean inView = (y >= contentTop) && ((y + w.getHeight()) <= contentBottom);
            w.visible = inView;
            w.active = inView;
        }
    }

    private Component toggleLabel(String name, boolean enabled) {
        return Component.literal(name + ": " + (enabled ? "ON" : "OFF"));
    }

    private int parseInt(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double parseDouble(EditBox box, double fallback) {
        try {
            return Double.parseDouble(box.getValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void saveChanges() {
        maxStormDebrisPerChunk = parseInt(maxDebrisBox, maxStormDebrisPerChunk);
        cloudRenderDistance = parseInt(cloudDistanceBox, cloudRenderDistance);
        stormBoostMultiplier = parseDouble(stormBoostMultiplierBox, stormBoostMultiplier);
        tornadoCheckIntervalSec = parseDouble(tornadoCheckIntervalBox, tornadoCheckIntervalSec);
        tornadoBaseSpawnRadiusM = parseDouble(tornadoBaseSpawnRadiusBox, tornadoBaseSpawnRadiusM);
        tornadoMinTempContrastC = parseDouble(tornadoMinTempContrastBox, tornadoMinTempContrastC);
        tornadoHumidityMinPercent = parseDouble(tornadoHumidityMinBox, tornadoHumidityMinPercent);
        tornadoPressureGradientGain = parseDouble(tornadoPressureGradientGainBox, tornadoPressureGradientGain);
        tornadoPressureGradientCap = parseDouble(tornadoPressureGradientCapBox, tornadoPressureGradientCap);
        tornadoShearMinSpeedDiffMps = parseDouble(tornadoShearMinSpeedDiffBox, tornadoShearMinSpeedDiffMps);
        tornadoShearMinDirDiffDeg = parseDouble(tornadoShearMinDirDiffBox, tornadoShearMinDirDiffDeg);
        tornadoStormMultiplier = parseDouble(tornadoStormMultiplierBox, tornadoStormMultiplier);
        tornadoRiskMinToConsider = parseDouble(tornadoRiskMinBox, tornadoRiskMinToConsider);
        tornadoBaseTriggerChance = parseDouble(tornadoBaseTriggerChanceBox, tornadoBaseTriggerChance);
        tornadoLapseRateCPer100m = parseDouble(tornadoLapseRateBox, tornadoLapseRateCPer100m);
        tornadoAloftDeltaHM = parseDouble(tornadoAloftDeltaBox, tornadoAloftDeltaHM);
        tornadoIntensityMin = parseDouble(tornadoIntensityMinBox, tornadoIntensityMin);
        tornadoIntensityMax = parseDouble(tornadoIntensityMaxBox, tornadoIntensityMax);
        tornadoCellCooldownMinutes = parseInt(tornadoCellCooldownBox, tornadoCellCooldownMinutes);
        tornadoRenderQuality = Mth.clamp(parseDouble(tornadoRenderQualityBox, tornadoRenderQuality), 0.25d, 1.0d);
        tornadoRenderDownsample = Mth.clamp(parseDouble(tornadoRenderDownsampleBox, tornadoRenderDownsample), 1.0d, 4.0d);
        hurricaneDestructionStrength = Mth.clamp(parseDouble(hurricaneDestructionStrengthBox, hurricaneDestructionStrength), 0.0d, 3.0d);
        // Les boutons ont deja bascule les booleens; aucun parsing requis.
        fogHumidityStartPercent = parseDouble(fogHumidityStartBox, fogHumidityStartPercent);
        fogHumidityFullPercent = parseDouble(fogHumidityFullBox, fogHumidityFullPercent);
        fogWetBiomeBaseStrength = parseDouble(fogWetBiomeStrengthBox, fogWetBiomeBaseStrength);
        fogRainBoost = parseDouble(fogRainBoostBox, fogRainBoost);
        fogFarDistance = parseDouble(fogFarDistanceBox, fogFarDistance);
        fogColorBlend = parseDouble(fogColorBlendBox, fogColorBlend);
        windBaseRetargetSec = parseDouble(windBaseRetargetBox, windBaseRetargetSec);
        windDirRetargetSec = parseDouble(windDirRetargetBox, windDirRetargetSec);
        windGustMeanSec = parseDouble(windGustMeanBox, windGustMeanSec);
        windGustDecayMps = parseDouble(windGustDecayBox, windGustDecayMps);
        windStormGustMult = parseDouble(windStormGustMultBox, windStormGustMult);
        windPushThresholdMps = parseDouble(windPushThresholdBox, windPushThresholdMps);
        windPlayerPushScale = parseDouble(windPlayerPushScaleBox, windPlayerPushScale);
        windEntityPushScale = parseDouble(windEntityPushScaleBox, windEntityPushScale);

        AtmoCommonConfig.FORCE_SHARED_EXECUTOR.set(forceSharedExecutor);
        AtmoCommonConfig.DISPLAY_UNITS_IMPERIAL.set(displayUnitsImperial);
        AtmoCommonConfig.ENABLE_TORNADOES.set(enableTornadoes);
        AtmoCommonConfig.ENABLE_TORNADO_DESTRUCTION.set(enableTornadoDestruction);
        AtmoCommonConfig.ENABLE_STORM_DEBRIS.set(enableStormDebris);
        AtmoCommonConfig.FOG_ENABLED.set(fogEnabled);
        AtmoCommonConfig.MAX_STORM_DEBRIS_PER_CHUNK.set(maxStormDebrisPerChunk);
        AtmoCommonConfig.CLOUD_RENDER_DISTANCE.set(cloudRenderDistance);
        AtmoCommonConfig.AUTO_REPAIR_GLASS.set(autoRepairGlass);
        AtmoCommonConfig.DAMAGE_GLASS_ON_TORNADO.set(damageGlassOnTornado);
        AtmoCommonConfig.ENABLE_HURRICANE_DESTRUCTION.set(enableHurricaneDestruction);
        AtmoCommonConfig.HURRICANE_DESTRUCTION_STRENGTH.set(hurricaneDestructionStrength);
        AtmoCommonConfig.HURRICANE_DROP_BROKEN_BLOCKS.set(hurricaneDropBrokenBlocks);
        AtmoCommonConfig.HURRICANE_DAMAGE_TREES.set(hurricaneDamageTrees);
        AtmoCommonConfig.TORNADO_CHECK_INTERVAL_SEC.set(tornadoCheckIntervalSec);
        AtmoCommonConfig.TORNADO_BASE_SPAWN_RADIUS_M.set(tornadoBaseSpawnRadiusM);
        AtmoCommonConfig.TORNADO_MIN_TEMP_CONTRAST_C.set(tornadoMinTempContrastC);
        AtmoCommonConfig.TORNADO_HUMIDITY_MIN_PERCENT.set(tornadoHumidityMinPercent);
        AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_GAIN.set(tornadoPressureGradientGain);
        AtmoCommonConfig.TORNADO_PRESSURE_GRADIENT_CAP.set(tornadoPressureGradientCap);
        AtmoCommonConfig.TORNADO_SHEAR_MIN_SPEED_DIFF_MPS.set(tornadoShearMinSpeedDiffMps);
        AtmoCommonConfig.TORNADO_SHEAR_MIN_DIR_DIFF_DEG.set(tornadoShearMinDirDiffDeg);
        AtmoCommonConfig.TORNADO_STORM_MULTIPLIER.set(tornadoStormMultiplier);
        AtmoCommonConfig.TORNADO_RISK_MIN_TO_CONSIDER.set(tornadoRiskMinToConsider);
        AtmoCommonConfig.TORNADO_BASE_TRIGGER_CHANCE.set(tornadoBaseTriggerChance);
        AtmoCommonConfig.TORNADO_LAPSE_RATE_C_PER_100M.set(tornadoLapseRateCPer100m);
        AtmoCommonConfig.TORNADO_ALOFT_DELTA_H_M.set(tornadoAloftDeltaHM);
        AtmoCommonConfig.TORNADO_INTENSITY_MIN.set(tornadoIntensityMin);
        AtmoCommonConfig.TORNADO_INTENSITY_MAX.set(tornadoIntensityMax);
        AtmoCommonConfig.TORNADO_CELL_COOLDOWN_MINUTES.set(tornadoCellCooldownMinutes);
        AtmoCommonConfig.TORNADO_ALLOW_LEGACY_FALLBACK.set(tornadoAllowLegacyFallback);
        AtmoCommonConfig.TORNADO_DEBUG_LOGGING.set(tornadoDebugLogging);
        AtmoCommonConfig.TORNADO_RENDER_QUALITY.set(tornadoRenderQuality);
        AtmoCommonConfig.TORNADO_RENDER_DOWNSAMPLE.set(tornadoRenderDownsample);
        AtmoCommonConfig.FOG_HUMIDITY_START_PERCENT.set(fogHumidityStartPercent);
        AtmoCommonConfig.FOG_HUMIDITY_FULL_PERCENT.set(fogHumidityFullPercent);
        AtmoCommonConfig.FOG_WET_BIOME_BASE_STRENGTH.set(fogWetBiomeBaseStrength);
        AtmoCommonConfig.FOG_RAIN_BOOST.set(fogRainBoost);
        AtmoCommonConfig.FOG_FAR_DISTANCE.set(fogFarDistance);
        AtmoCommonConfig.FOG_COLOR_BLEND.set(fogColorBlend);
        AtmoCommonConfig.WIND_BASE_RETARGET_SEC.set(windBaseRetargetSec);
        AtmoCommonConfig.WIND_DIR_RETARGET_SEC.set(windDirRetargetSec);
        AtmoCommonConfig.WIND_GUST_MEAN_SEC.set(windGustMeanSec);
        AtmoCommonConfig.WIND_GUST_DECAY_MPS.set(windGustDecayMps);
        AtmoCommonConfig.WIND_STORM_GUST_MULT.set(windStormGustMult);
        AtmoCommonConfig.WIND_PUSH_THRESHOLD_MPS.set(windPushThresholdMps);
        AtmoCommonConfig.WIND_PLAYER_PUSH_SCALE.set(windPlayerPushScale);
        AtmoCommonConfig.WIND_ENTITY_PUSH_SCALE.set(windEntityPushScale);
        AtmoCommonConfig.STORM_SEVERITY_BOOSTER.set(stormBoostMultiplier);
        AtmoCommonConfig.DEBUG_MODE.set(debugMode);
        ProjectAtmosphere.DEBUG_MODE = debugMode;

        try {
            saveCommonConfigForMod(ProjectAtmosphere.MODID);
        } catch (Exception ignored) {
        }
    }

    /** Finds this mod's COMMON config and saves it. */
    private static void saveCommonConfigForMod(String modId) {
        var set = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON);
        if (set == null) return;
        for (ModConfig cfg : set) {
            if (cfg.getModId().equals(modId)) {
                cfg.save();
                return;
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}


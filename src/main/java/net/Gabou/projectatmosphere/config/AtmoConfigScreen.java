package net.Gabou.projectatmosphere.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Simple in-game configuration screen for Project Atmosphere.
 */
public class AtmoConfigScreen extends Screen {
    private final Screen parent;
    private boolean forceSharedExecutor;
    private boolean enableStormDebris;
    private int maxStormDebrisPerChunk;
    private boolean autoRepairGlass;
    private boolean damageGlassOnTornado;
    private EditBox maxDebrisBox;

    public AtmoConfigScreen(Screen parent) {
        super(Component.literal("Project Atmosphere Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.forceSharedExecutor = AtmoCommonConfig.FORCE_SHARED_EXECUTOR.get();
        this.enableStormDebris = AtmoCommonConfig.ENABLE_STORM_DEBRIS.get();
        this.maxStormDebrisPerChunk = AtmoCommonConfig.MAX_STORM_DEBRIS_PER_CHUNK.get();
        this.autoRepairGlass = AtmoCommonConfig.AUTO_REPAIR_GLASS.get();
        this.damageGlassOnTornado = AtmoCommonConfig.DAMAGE_GLASS_ON_TORNADO.get();

        int center = this.width / 2;
        int y = 40;

        addRenderableWidget(Button.builder(toggleLabel("Force Shared Executor", forceSharedExecutor), b -> {
            forceSharedExecutor = !forceSharedExecutor;
            b.setMessage(toggleLabel("Force Shared Executor", forceSharedExecutor));
        }).bounds(center - 100, y, 200, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(toggleLabel("Storm Debris", enableStormDebris), b -> {
            enableStormDebris = !enableStormDebris;
            b.setMessage(toggleLabel("Storm Debris", enableStormDebris));
        }).bounds(center - 100, y, 200, 20).build());
        y += 24;

        this.maxDebrisBox = new EditBox(this.font, center - 100, y, 200, 20, Component.literal("Max Debris"));
        this.maxDebrisBox.setValue(Integer.toString(maxStormDebrisPerChunk));
        addRenderableWidget(this.maxDebrisBox);
        y += 24;

        addRenderableWidget(Button.builder(toggleLabel("Auto Repair Glass", autoRepairGlass), b -> {
            autoRepairGlass = !autoRepairGlass;
            b.setMessage(toggleLabel("Auto Repair Glass", autoRepairGlass));
        }).bounds(center - 100, y, 200, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(toggleLabel("Damage Glass On Tornado", damageGlassOnTornado), b -> {
            damageGlassOnTornado = !damageGlassOnTornado;
            b.setMessage(toggleLabel("Damage Glass On Tornado", damageGlassOnTornado));
        }).bounds(center - 100, y, 200, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            saveChanges();
            Minecraft.getInstance().setScreen(parent);
        }).bounds(center - 100, this.height - 27, 200, 20).build());
    }

    private Component toggleLabel(String name, boolean enabled) {
        return Component.literal(name + ": " + (enabled ? "ON" : "OFF"));
    }

    private void saveChanges() {
        int parsed = this.maxStormDebrisPerChunk;
        try {
            parsed = Integer.parseInt(this.maxDebrisBox.getValue());
            errorMessage = null;
        } catch (NumberFormatException ignored) {
            errorMessage = Component.translatable("Invalid number for Max Storm Debris per Chunk.");
        }
        AtmoCommonConfig.FORCE_SHARED_EXECUTOR.set(forceSharedExecutor);
        AtmoCommonConfig.ENABLE_STORM_DEBRIS.set(enableStormDebris);
        AtmoCommonConfig.MAX_STORM_DEBRIS_PER_CHUNK.set(parsed);
        AtmoCommonConfig.AUTO_REPAIR_GLASS.set(autoRepairGlass);
        AtmoCommonConfig.DAMAGE_GLASS_ON_TORNADO.set(damageGlassOnTornado);
        ConfigTracker.INSTANCE.saveConfigs(ModConfig.Type.COMMON);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}


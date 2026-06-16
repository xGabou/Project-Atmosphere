package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.lighting.CloudLightingManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Conservative world darkening for players beneath significant storm clouds.
 * Works without shaderpack cooperation via fog biasing and optional terrain overlay.
 */
public final class FallbackDarkeningPass {
    private static final float TERRAIN_OVERLAY_THRESHOLD = 0.07F;
    private static final float FOG_DARKEN_THRESHOLD = 0.03F;
    private static final float MAX_FOG_DARKEN = 0.34F;
    private static final float MAX_FOG_FAR_REDUCTION = 0.22F;

    private FallbackDarkeningPass() {
    }

    public static void updateFrame(@NotNull CloudRenderFrameContext frameContext, @Nullable ClientLevel level) {
        if (level == null) {
            CloudLightingManager.clear();
            return;
        }
        CloudLightingManager.update(level, frameContext.getCameraPosition(), frameContext.getPartialTick());
    }

    public static boolean applyTerrainDarkening(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull RenderTarget mainTarget,
            @Nullable RenderTarget shadowTarget
    ) {
        // GPU terrain-shadow texture upload is disabled; CPU shadow data still drives fog/player darkening.
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || !isEnabled()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            return;
        }
        ensureLightingUpdated(level, event.getCamera().getPosition(), (float) event.getPartialTick());
        float darkness = combinedDarkeningFactor();
        if (darkness < FOG_DARKEN_THRESHOLD) {
            return;
        }

        float farReduction = Mth.clamp(darkness * MAX_FOG_FAR_REDUCTION, 0.0F, MAX_FOG_FAR_REDUCTION);
        float baseFar = event.getFarPlaneDistance();
        event.setFarPlaneDistance(Math.max(2.0F, baseFar * (1.0F - farReduction)));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!isEnabled()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            return;
        }
        ensureLightingUpdated(level, event.getCamera().getPosition(), (float) event.getPartialTick());

        float darkness = combinedDarkeningFactor();
        if (darkness < FOG_DARKEN_THRESHOLD) {
            return;
        }

        float darken = Mth.clamp(darkness * MAX_FOG_DARKEN, 0.0F, MAX_FOG_DARKEN);
        float stormTint = Mth.clamp(CloudLightingManager.getPlayerStormDarknessFactor() * 0.12F, 0.0F, 0.10F);
        float redScale = 1.0F - darken * 0.88F - stormTint * 0.35F;
        float greenScale = 1.0F - darken * 0.92F - stormTint * 0.20F;
        float blueScale = 1.0F - darken * 0.96F;

        event.setRed(Mth.clamp(event.getRed() * redScale, 0.0F, 1.0F));
        event.setGreen(Mth.clamp(event.getGreen() * greenScale, 0.0F, 1.0F));
        event.setBlue(Mth.clamp(event.getBlue() * blueScale, 0.0F, 1.0F));
    }

    public static boolean shouldApplyTerrainOverlay() {
        return !ClientShaderPipelineHelper.isConservativeShaderPathPreferred();
    }

    private static void ensureLightingUpdated(@NotNull ClientLevel level, @NotNull Vec3 cameraPosition, float partialTick) {
        if (!AtmosphereCloudPolicy.shouldRenderPaClouds(level)) {
            CloudLightingManager.clear();
            return;
        }
        CloudLightingManager.update(level, cameraPosition, partialTick);
    }

    private static float combinedDarkeningFactor() {
        return Mth.clamp(
                CloudLightingManager.getPlayerCloudDarknessFactor() * 0.62F
                        + CloudLightingManager.getPlayerStormDarknessFactor() * 0.28F
                        + CloudLightingManager.getPlayerShadowIntensity() * 0.10F,
                0.0F,
                1.0F
        );
    }

    private static float resolveTerrainOverlayStrength() {
        float shadow = CloudLightingManager.getPlayerShadowIntensity();
        float storm = CloudLightingManager.getPlayerStormDarknessFactor();
        return Mth.clamp(0.34F + shadow * 0.42F + storm * 0.18F, 0.28F, 0.68F);
    }

    private static boolean isEnabled() {
        try {
            return AtmoCommonConfig.ENABLE_CLOUD_SHADOW_MAP.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }
}

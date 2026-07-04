package net.Gabou.projectatmosphere.client.fog;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.WorldEffects;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendResolver;
import net.Gabou.projectatmosphere.clouds.backend.CloudVisualBackend;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.commons.lang3.tuple.Pair;

public final class SimpleCloudsWhiteoutFogHandler {
    private SimpleCloudsWhiteoutFogHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (!shouldApply(level)) {
            return;
        }

        SimpleCloudsRenderer renderer = SimpleCloudsRenderer.getOptionalInstance().orElse(null);
        float partialTick = (float) event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        float cloudWhiteout = renderer == null ? 0.0F : computeCloudWhiteout(level, renderer, cameraPos);
        float whiteout = cloudWhiteout;

        if (whiteout <= 0.0F) {
            return;
        }

        float baseFar = event.getFarPlaneDistance();
        event.setNearPlaneDistance(0.0F);
        event.setFarPlaneDistance(Math.min(baseFar, Math.max(0.35F, Mth.lerp(whiteout, baseFar, 0.85F))));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!shouldApply(level)) {
            return;
        }

        SimpleCloudsRenderer renderer = SimpleCloudsRenderer.getOptionalInstance().orElse(null);
        float partialTick = (float) event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        float cloudWhiteout = renderer == null ? 0.0F : computeCloudWhiteout(level, renderer, cameraPos);
        float whiteout = cloudWhiteout;

        if (whiteout <= 0.0F) {
            return;
        }

        float[] cloudColor = renderer == null ? null : renderer.getCloudColor(partialTick);
        float dustyRed = cloudColor == null ? 0.30F : Mth.clamp(cloudColor[0] * 0.62F + 0.10F, 0.0F, 1.0F);
        float dustyGreen = cloudColor == null ? 0.25F : Mth.clamp(cloudColor[1] * 0.55F + 0.08F, 0.0F, 1.0F);
        float dustyBlue = cloudColor == null ? 0.20F : Mth.clamp(cloudColor[2] * 0.45F + 0.06F, 0.0F, 1.0F);
        float dustyBlend = Mth.clamp(whiteout * 1.18F, 0.0F, 1.0F);
        event.setRed(Mth.lerp(dustyBlend, event.getRed(), dustyRed));
        event.setGreen(Mth.lerp(dustyBlend, event.getGreen(), dustyGreen));
        event.setBlue(Mth.lerp(dustyBlend, event.getBlue(), dustyBlue));
    }

    private static float computeCloudWhiteout(ClientLevel level, SimpleCloudsRenderer renderer, Vec3 cameraPos) {
        WorldEffects effects = renderer.getWorldEffectsManager();
        CloudType type = effects.getCloudTypeAtCamera();
        float fade = effects.getFadeRegionAtCamera();

        if (type == null || type == SimpleCloudsConstants.EMPTY) {
            Pair<CloudType, Float> fallback = CloudManager.get(level).getCloudTypeAtWorldPos((float) cameraPos.x, (float) cameraPos.z);
            type = fallback.getLeft();
            fade = fallback.getRight();
        }

        if (type == null || type == SimpleCloudsConstants.EMPTY) {
            return 0.0F;
        }

        float cloudBase = CloudManager.get(level).getCloudHeight();
        float bottom = cloudBase + type.noiseConfig().getStartHeight() * SimpleCloudsConstants.CLOUD_SCALE;
        float top = cloudBase + type.noiseConfig().getEndHeight() * SimpleCloudsConstants.CLOUD_SCALE;

        if (cameraPos.y < bottom || cameraPos.y > top) {
            return 0.0F;
        }

        float horizontal = 1.0F - Mth.clamp(fade, 0.0F, 1.0F);
        float distToVerticalEdge = (float) Math.min(cameraPos.y - bottom, top - cameraPos.y);
        float vertical = 1.0F - Mth.clamp(distToVerticalEdge / 16.0F, 0.0F, 1.0F);
        return Mth.clamp(horizontal * vertical, 0.0F, 1.0F);
    }

    private static boolean shouldApply(ClientLevel level) {
        if (level == null) {
            return false;
        }
        try {
            if (AtmoCommonConfig.CLOUD_VOLUMETRIC_RENDERER_ENABLED.get()
                    && AtmoCommonConfig.CLOUD_FIELD_RENDERER_ENABLED.get()) {
                return false;
            }
        } catch (IllegalStateException ignored) {
            return false;
        }
        return CloudBackendResolver.resolve(level) == CloudVisualBackend.SIMPLE_CLOUDS;
    }
}

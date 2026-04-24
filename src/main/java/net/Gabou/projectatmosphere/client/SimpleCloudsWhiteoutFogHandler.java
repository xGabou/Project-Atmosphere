package net.Gabou.projectatmosphere.client;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.WorldEffects;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.fog.AtmosphereFogState;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsTornadoRenderer;
import net.Gabou.projectatmosphere.modules.fog.FogHeuristics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SimpleCloudsWhiteoutFogHandler {
    private SimpleCloudsWhiteoutFogHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        SimpleCloudsRenderer renderer = SimpleCloudsRenderer.getOptionalInstance().orElse(null);
        float partialTick = (float) event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        FogHeuristics.FogProfile dynamicFog = AtmosphereFogState.sample(level, cameraPos, partialTick);
        float cloudWhiteout = renderer == null ? 0.0F : computeCloudWhiteout(level, renderer, cameraPos);
        float tornadoWhiteout = SimpleCloudsTornadoRenderer.INSTANCE.sampleWhiteoutAtCamera(level, cameraPos, partialTick);
        float whiteout = Math.max(cloudWhiteout, tornadoWhiteout);
        float dynamicStrength = dynamicFog.strength();

        if (whiteout <= 0.0F && dynamicStrength <= 0.0F) {
            return;
        }

        float baseNear = event.getNearPlaneDistance();
        float baseFar = event.getFarPlaneDistance();
        float nearPlane = baseNear;
        float farPlane = baseFar;

        if (dynamicStrength > 0.0F && Level.OVERWORLD.equals(level.dimension())) {
            float fogInfluence = Mth.clamp(
                    dynamicStrength * 0.55F
                            + dynamicStrength * dynamicStrength * 0.85F
                            + dynamicFog.wetBiomeFactor() * 0.20F
                            + dynamicFog.rainFactor() * 0.12F,
                    0.0F,
                    1.0F
            );
            float configuredNear = AtmoCommonConfig.FOG_NEAR_DISTANCE.get().floatValue();
            float configuredFar = AtmoCommonConfig.FOG_FAR_DISTANCE.get().floatValue();
            nearPlane = Math.min(nearPlane, Mth.lerp(fogInfluence, baseNear, configuredNear));
            farPlane = Math.min(farPlane, Math.max(2.0F, Mth.lerp(fogInfluence, baseFar, configuredFar)));
        }
        if (whiteout > 0.0F) {
            nearPlane = 0.0F;
            farPlane = Math.min(farPlane, Math.max(0.5F, Mth.lerp(whiteout, baseFar, 6.0F)));
        }

        event.setNearPlaneDistance(nearPlane);
        event.setFarPlaneDistance(farPlane);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        SimpleCloudsRenderer renderer = SimpleCloudsRenderer.getOptionalInstance().orElse(null);
        float partialTick = (float) event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        FogHeuristics.FogProfile dynamicFog = AtmosphereFogState.sample(level, cameraPos, partialTick);
        float cloudWhiteout = renderer == null ? 0.0F : computeCloudWhiteout(level, renderer, cameraPos);
        float tornadoWhiteout = SimpleCloudsTornadoRenderer.INSTANCE.sampleWhiteoutAtCamera(level, cameraPos, partialTick);
        float whiteout = Math.max(cloudWhiteout, tornadoWhiteout);
        float dynamicStrength = dynamicFog.strength();

        if (whiteout <= 0.0F && dynamicStrength <= 0.0F) {
            return;
        }

        if (dynamicStrength > 0.0F && Level.OVERWORLD.equals(level.dimension())) {
            float fogInfluence = Mth.clamp(
                    dynamicStrength * 0.55F
                            + dynamicStrength * dynamicStrength * 0.85F
                            + dynamicFog.wetBiomeFactor() * 0.20F
                            + dynamicFog.rainFactor() * 0.12F,
                    0.0F,
                    1.0F
            );
            float colorBlend = AtmoCommonConfig.FOG_COLOR_BLEND.get().floatValue()
                    * Mth.clamp(dynamicStrength * 0.45F + fogInfluence * 0.70F, 0.0F, 1.0F);
            float dampRed = Mth.clamp(event.getRed() * (0.88F - dynamicFog.wetBiomeFactor() * 0.06F), 0.0F, 1.0F);
            float dampGreen = Mth.clamp(event.getGreen() * (0.90F + dynamicFog.wetBiomeFactor() * 0.05F), 0.0F, 1.0F);
            float dampBlue = Mth.clamp(event.getBlue() * (0.95F + dynamicFog.rainFactor() * 0.05F), 0.0F, 1.0F);
            event.setRed(Mth.lerp(colorBlend, event.getRed(), dampRed));
            event.setGreen(Mth.lerp(colorBlend, event.getGreen(), dampGreen));
            event.setBlue(Mth.lerp(colorBlend, event.getBlue(), dampBlue));
        }

        if (whiteout > 0.0F && renderer != null) {
            float[] cloudColor = renderer.getCloudColor(partialTick);
            event.setRed(Mth.lerp(whiteout, event.getRed(), cloudColor[0]));
            event.setGreen(Mth.lerp(whiteout, event.getGreen(), cloudColor[1]));
            event.setBlue(Mth.lerp(whiteout, event.getBlue(), cloudColor[2]));
        }
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
}

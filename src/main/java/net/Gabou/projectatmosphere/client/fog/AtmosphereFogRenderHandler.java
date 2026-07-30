package net.Gabou.projectatmosphere.client.fog;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.fog.FogHeuristics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

public final class AtmosphereFogRenderHandler {
    private AtmosphereFogRenderHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        float partialTick = (float) event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        FogHeuristics.FogProfile dynamicFog = AtmosphereFogState.sample(level, cameraPos, partialTick);
        float dynamicStrength = dynamicFog.strength();
        if (dynamicStrength <= 0.0F) {
            return;
        }

        float fogInfluence = computeFogInfluence(dynamicFog);
        float baseNear = event.getNearPlaneDistance();
        float baseFar = event.getFarPlaneDistance();
        float configuredNear = AtmoCommonConfig.FOG_NEAR_DISTANCE.get().floatValue();
        float configuredFar = AtmoCommonConfig.FOG_FAR_DISTANCE.get().floatValue();

        event.setNearPlaneDistance(Math.min(baseNear, Mth.lerp(fogInfluence, baseNear, configuredNear)));
        event.setFarPlaneDistance(Math.min(baseFar, Math.max(2.0F, Mth.lerp(fogInfluence, baseFar, configuredFar))));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        float partialTick = (float) event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        FogHeuristics.FogProfile dynamicFog = AtmosphereFogState.sample(level, cameraPos, partialTick);
        float dynamicStrength = dynamicFog.strength();
        if (dynamicStrength <= 0.0F) {
            return;
        }

        float fogInfluence = computeFogInfluence(dynamicFog);
        float colorBlend = AtmoCommonConfig.FOG_COLOR_BLEND.get().floatValue()
                * Mth.clamp(dynamicStrength * 0.45F + fogInfluence * 0.70F, 0.0F, 1.0F);
        float dampRed = Mth.clamp(event.getRed() * (0.88F - dynamicFog.wetBiomeFactor() * 0.06F), 0.0F, 1.0F);
        float dampGreen = Mth.clamp(event.getGreen() * (0.90F + dynamicFog.wetBiomeFactor() * 0.05F), 0.0F, 1.0F);
        float dampBlue = Mth.clamp(event.getBlue() * (0.95F + dynamicFog.rainFactor() * 0.05F), 0.0F, 1.0F);
        event.setRed(Mth.lerp(colorBlend, event.getRed(), dampRed));
        event.setGreen(Mth.lerp(colorBlend, event.getGreen(), dampGreen));
        event.setBlue(Mth.lerp(colorBlend, event.getBlue(), dampBlue));
    }

    private static float computeFogInfluence(FogHeuristics.FogProfile dynamicFog) {
        float dynamicStrength = dynamicFog.strength();
        return Mth.clamp(
                dynamicStrength * 0.55F
                        + dynamicStrength * dynamicStrength * 0.85F
                        + dynamicFog.wetBiomeFactor() * 0.20F
                        + dynamicFog.rainFactor() * 0.12F,
                0.0F,
                1.0F
        );
    }
}

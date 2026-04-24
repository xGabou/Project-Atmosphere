package net.Gabou.projectatmosphere.mixin.compat.auroras;

import auroras.util.AuroraData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.client.render.SkyEffectState;
import net.Gabou.projectatmosphere.compat.auroras.AuroraCompatController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "auroras.util.AuroraRenderer", remap = false)
public abstract class AuroraRendererMixin {

    @ModifyVariable(
            method = "render(Lauroras/util/AuroraData;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;F)V",
            at = @At(value = "STORE"),
            ordinal = 0,
            index = 7,
            require = 0
    )
    private float projectatmosphere$scaleAuroraBrightness(float nlBrightness) {
        return AuroraCompatController.scaleBrightness(nlBrightness);
    }

    @ModifyVariable(
            method = "render(Lauroras/util/AuroraData;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;F)V",
            at = @At(value = "STORE"),
            ordinal = 0,
            index = 20,
            require = 0
    )
    private float projectatmosphere$overrideRainLevel(float rainLevel) {
        return AuroraCompatController.overrideRainLevel(rainLevel);
    }

    @Inject(
            method = "render(Lauroras/util/AuroraData;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;F)V",
            at = @At("RETURN")
    )
    private void projectatmosphere$publishAuroraState(AuroraData auroraData,
                                                      Minecraft minecraft,
                                                      Level level,
                                                      PoseStack poseStack,
                                                      Matrix4f projectionMatrix,
                                                      float partialTick,
                                                      CallbackInfo ci) {
        boolean active = minecraft != null && minecraft.player != null && AuroraCompatController.isActive();
        SkyEffectState.setAurora(active, active ? minecraft.player.position() : null);
    }
}

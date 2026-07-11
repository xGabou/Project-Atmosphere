package net.Gabou.projectatmosphere.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.client.render.ClientCloudRenderOwnership;
import net.Gabou.projectatmosphere.clouds.client.render.CustomPrecipitationRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/*
 * Disclaimer: This class was originally taken from Simple Clouds for Forge 1.20.1
 * and adapted for Project Atmosphere's cloud renderer.
 *
 * Thanks to nonamecrackers for the inspiration.
 */

@Mixin(value = LevelRenderer.class, priority = 1001)
public class MixinLevelRenderer {
    @Shadow
    private @Nullable ClientLevel level;


    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    public void projectAtmosphere$overrideCloudRendering_renderClouds(PoseStack stack, Matrix4f projMat, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        if (ClientCloudRenderOwnership.ownsBaseCloudRendering(this.level)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    public void projectatmosphere$customRainHook_renderSnowAndRain(LightTexture texture, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        if (CustomPrecipitationRenderer.renderSnowAndRain(this.level, texture, partialTick, camX, camY, camZ)) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "tickRain", constant = @Constant(floatValue = 0.2F, ordinal = 0))
    public float projectatmosphere$scaleRainSoundVolume_tickRain(float value) {
        if (this.level == null || !AtmosphereCloudPolicy.shouldOwnWeather(this.level)) {
            return value;
        }
        return value * this.level.getRainLevel(0.0F);
    }

    @ModifyConstant(method = "tickRain", constant = @Constant(floatValue = 0.1F, ordinal = 0))
    public float projectatmosphere$scaleAboveRainSoundVolume_tickRain(float value) {
        if (this.level == null || !AtmosphereCloudPolicy.shouldOwnWeather(this.level)) {
            return value;
        }
        return value * this.level.getRainLevel(0.0F);
    }

}

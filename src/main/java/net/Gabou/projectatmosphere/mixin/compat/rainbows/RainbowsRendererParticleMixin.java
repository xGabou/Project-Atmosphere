package net.Gabou.projectatmosphere.mixin.compat.rainbows;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.Gabou.projectatmosphere.client.render.SkyEffectState;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "rainbows.util.RainbowsRendererParticle", remap = false)
public abstract class RainbowsRendererParticleMixin {

    @Shadow public double rainbowTick;

    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
            at = @At(value = "STORE"),
            ordinal = 0,
            index = 5,
            require = 0
    )
    private float projectatmosphere$overrideRainLevel(float rainLevel) {
        return RainbowWeatherTracker.getRainLevelOverride(rainLevel);
    }

    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
            at = @At(value = "STORE"),
            ordinal = 0,
            index = 7,
            require = 0
    )
    private double projectatmosphere$scaleRainbowBrightness(double brightness) {
        return RainbowWeatherTracker.scaleBrightness(brightness);
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
            at = @At("HEAD")
    )
    private void projectatmosphere$syncRainbowLifecycle(VertexConsumer buffer,
                                                        Camera camera,
                                                        float partialTicks,
                                                        CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !RainbowWeatherTracker.isEnabled()) {
            SkyEffectState.setRainbow(false, null);
            return;
        }

        if ((RainbowWeatherTracker.shouldRender() || RainbowWeatherTracker.consumeActivationPulse()) && this.rainbowTick <= 0.0D) {
            this.rainbowTick = 5000.0D;
        }
        if (!RainbowWeatherTracker.shouldRender() && RainbowWeatherTracker.getVisualStrength() < 0.02F) {
            this.rainbowTick = 0.0D;
        }
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
            at = @At("RETURN")
    )
    private void projectatmosphere$publishRainbowState(VertexConsumer buffer,
                                                       Camera camera,
                                                       float partialTicks,
                                                       CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        boolean active = mc.level != null
                && RainbowWeatherTracker.shouldRender()
                && this.rainbowTick > 0.0D
                && RainbowWeatherTracker.getVisualStrength() > 0.02F;
        SkyEffectState.setRainbow(active, active && mc.player != null ? mc.player.position() : null);
    }
}

package net.Gabou.projectatmosphere.mixin.compat.rainbows;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.Gabou.projectatmosphere.client.render.SkyEffectState;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rainbows.util.RainbowsRendererParticle;

/**
 * Adjusts rainbow rendering to follow Project Atmosphere weather.
 */
@Mixin(value = RainbowsRendererParticle.class, remap = false)
public abstract class RainbowsRendererParticleMixin {

    @Shadow public double rainbowTick;

    /**
     * Replaces the rain level float when the renderer calls level.getRainLevel(partialTicks).
     * The target is the first stored float local in render().
     */
    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
            at = @At(value = "STORE"),
            ordinal = 0,
            require = 0
    )
    private float projectatmosphere$overrideRainLevel(float rainLevel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !RainbowWeatherTracker.isEnabled())
            return rainLevel;
        float tracked = RainbowWeatherTracker.getRainLevel(mc.level.dimension());
        if (tracked > 0.01f) {
            SkyEffectState.setRainbow(false, null);
        }
        return tracked;
    }

    /**
     * Hooks right before Math.sin() is called to optionally trigger rainbow appearance
     * when rain stops, depending on Project Atmosphere’s tracker.
     */
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;sin(D)D")
    )
    private void projectatmosphere$triggerWhenRainStops(VertexConsumer buffer,
                                                        net.minecraft.client.Camera camera,
                                                        float partialTicks,
                                                        CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !RainbowWeatherTracker.isEnabled())
            return;

        float rain = RainbowWeatherTracker.getRainLevel(mc.level.dimension());
        if (rain > 0.01f) {
            SkyEffectState.setRainbow(false, null);
            return;
        }

        if (RainbowWeatherTracker.consumeRainStop(mc.level.dimension())) {
            double time = mc.level.getTimeOfDay(partialTicks);
            double brightness = Mth.clamp(Math.cos(Math.PI * 2 * time) * 3.0, 0.0, 1.0);
            if (brightness > 0.0 && this.rainbowTick <= 0.0)
                this.rainbowTick = 5000.0;
        }
        if (this.rainbowTick > 0.0) {
            SkyEffectState.setRainbow(true, mc.player != null ? mc.player.position() : null);
        }
    }
}

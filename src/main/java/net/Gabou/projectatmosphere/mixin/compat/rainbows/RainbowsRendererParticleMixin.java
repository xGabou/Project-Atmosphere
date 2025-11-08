package net.Gabou.projectatmosphere.mixin.compat.rainbows;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rainbows.util.RainbowsRendererParticle;

@Mixin(value = RainbowsRendererParticle.class, remap = false)
public abstract class RainbowsRendererParticleMixin {
    
    @Shadow public double rainbowTick;

    @ModifyVariable(
            method = "render",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private float projectatmosphere$overrideRainLevel(float rainLevel,
                                                      VertexConsumer buffer,
                                                      Camera camera,
                                                      float partialTicks) {
        if (!RainbowWeatherTracker.isEnabled() || Minecraft.getInstance().level == null) {
            return rainLevel;
        }
        return RainbowWeatherTracker.getRainLevel(Minecraft.getInstance().level.dimension());
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;sin(D)D"),
            cancellable = false
    )
    private void projectatmosphere$triggerWhenRainStops(VertexConsumer buffer,
                                                        Camera camera,
                                                        float partialTicks,
                                                        CallbackInfo ci) {
        if (!RainbowWeatherTracker.isEnabled() || Minecraft.getInstance().level == null) {
            return;
        }
        if (RainbowWeatherTracker.consumeRainStop(Minecraft.getInstance().level.dimension())) {
            double time = Minecraft.getInstance().level.getTimeOfDay(partialTicks);
            double brightness = Mth.clamp(Math.cos(Math.PI * 2 * time) * 3.0, 0.0, 1.0);
            if (brightness > 0.0 && this.rainbowTick <= 0.0) {
                this.rainbowTick = 5000.0;
            }
        }
    }
}

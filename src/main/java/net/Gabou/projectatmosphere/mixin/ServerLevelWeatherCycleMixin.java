package net.Gabou.projectatmosphere.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherCycleMixin {

    @Shadow
    protected abstract void resetWeatherCycle();

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$disableVanillaWeatherCycle(CallbackInfo ci) {
        this.resetWeatherCycle();
        ci.cancel();
    }
}

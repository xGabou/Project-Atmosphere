package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.manager.LocalizedPrecipitationBlockUpdater;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
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
        if (!AtmosphereCloudPolicy.shouldOwnWeather((ServerLevel) (Object) this)) {
            return;
        }
        this.resetWeatherCycle();
        ci.cancel();
    }

    @Inject(method = "tickChunk", at = @At("RETURN"))
    private void projectatmosphere$tickLocalizedPrecipitationBlocks(LevelChunk chunk, int tickSpeed, CallbackInfo ci) {
        LocalizedPrecipitationBlockUpdater.tickChunk((ServerLevel) (Object) this, chunk);
    }
}

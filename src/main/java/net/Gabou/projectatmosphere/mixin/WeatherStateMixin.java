package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.clouds.WeatherCloudQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class WeatherStateMixin {

    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$isRainingAt(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(WeatherCloudQueries.isRainingAt((Level) (Object) this, pos));
    }

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$isRaining(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(WeatherCloudQueries.isRaining((Level) (Object) this));
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$isThundering(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(WeatherCloudQueries.isThundering((Level) (Object) this));
    }

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$getRainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(WeatherCloudQueries.getRainLevel((Level) (Object) this, partialTick));
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$getThunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(WeatherCloudQueries.getThunderLevel((Level) (Object) this, partialTick));
    }
}

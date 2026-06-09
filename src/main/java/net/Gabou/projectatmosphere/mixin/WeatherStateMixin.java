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
        if (WeatherCloudQueries.isRainingAt((Level) (Object) this, pos)) {
            cir.setReturnValue(true);
        }
    }
}

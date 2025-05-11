package net.Gabou.projectatmosphere.mixin;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class OptionsMixin {

    @Inject(method = "load(Z)V", at = @At("TAIL"))
    private void forceCloudStatusOff(boolean limited, CallbackInfo ci) {
        Options options = (Options)(Object)this;

        if (options.cloudStatus().get() != CloudStatus.OFF) {
            options.cloudStatus().set(CloudStatus.OFF);
        }
    }
}
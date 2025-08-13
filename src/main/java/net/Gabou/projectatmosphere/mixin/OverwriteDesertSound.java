package net.Gabou.projectatmosphere.mixin;

import com.BreadRes.desertstormwarming.client.SandstormSoundScheduler;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = SandstormSoundScheduler.class, remap = false)
public class OverwriteDesertSound {
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true)
    private static void onClientTick(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if( event.phase != TickEvent.Phase.START || event.side.isServer()) {
            return;
        }

        ci.cancel();
    }
}

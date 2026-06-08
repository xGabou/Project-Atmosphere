package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.command.WeatherCommandBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.WeatherCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WeatherCommand.class, priority = 2000)
public abstract class WeatherCommandMixin {

    @Inject(method = "setClear", at = @At("HEAD"), cancellable = true)
    private static void projectatmosphere$setClear(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(WeatherCommandBridge.setClear(source, duration));
    }

    @Inject(method = "setRain", at = @At("HEAD"), cancellable = true)
    private static void projectatmosphere$setRain(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(WeatherCommandBridge.setRain(source, duration));
    }

    @Inject(method = "setThunder", at = @At("HEAD"), cancellable = true)
    private static void projectatmosphere$setThunder(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(WeatherCommandBridge.setThunder(source, duration));
    }
}

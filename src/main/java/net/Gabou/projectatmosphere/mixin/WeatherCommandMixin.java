package net.Gabou.projectatmosphere.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.WeatherCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherCommand.class)
public class WeatherCommandMixin {

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void onRegister(CommandDispatcher<CommandSourceStack> p_139167_, CallbackInfo ci) {
        // Cancel the command registration
        ci.cancel();
    }
}
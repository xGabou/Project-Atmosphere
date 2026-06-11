package net.Gabou.projectatmosphere.mixin.client;

import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.client.ClientLocalizedWeatherState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class ClientLevelWeatherMixin {

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$isRaining(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ClientLevel clientLevel
                && AtmosphereCloudPolicy.shouldOwnWeather(clientLevel)
                && ClientLocalizedWeatherState.isRaining(clientLevel)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$isThundering(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ClientLevel clientLevel
                && AtmosphereCloudPolicy.shouldOwnWeather(clientLevel)
                && ClientLocalizedWeatherState.isThundering(clientLevel)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void projectatmosphere$getRainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel clientLevel)) {
            return;
        }
        if (!AtmosphereCloudPolicy.shouldOwnWeather(clientLevel)) {
            return;
        }

        float rainLevel = ClientLocalizedWeatherState.getRainLevel(clientLevel, partialTick);
        if (rainLevel > 0.02F) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), rainLevel));
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void projectatmosphere$getThunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel clientLevel)) {
            return;
        }
        if (!AtmosphereCloudPolicy.shouldOwnWeather(clientLevel)) {
            return;
        }

        float thunderLevel = ClientLocalizedWeatherState.getThunderLevel(clientLevel, partialTick);
        if (thunderLevel > 0.02F) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), thunderLevel));
        }
    }
}

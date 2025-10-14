package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.client.BiomeClientTemperatureCache;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sereneseasons.season.SeasonHooks;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

/**
 * Injected by Project Atmosphere to replace Serene Seasons'
 * temperature computation with PA's real forecast system.
 * <p>
 * All SS+ systems automatically inherit these values.
 */
@Mixin(value = SeasonHooks.class, remap = false)
public class SeasonHooksMixin {
    @Inject(
            method = "warmEnoughToRainSeasonal(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void redirectTemperatureToAtmosphere(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Only operate on the server to avoid client desyncs
        if (level instanceof ServerLevel serverLevel) {


            // Build a BiomeInstanceKey for PA
            BiomeInstanceKey key = new BiomeInstanceKey(
                    serverLevel.getBiome(pos).unwrapKey().orElseThrow().location(),
                    pos
            );

            // Get real forecasted temperature
            float temperature = ForecastOrchestrator.getCurrentTemperature(key, serverLevel.getDayTime());

            // If below 0°C → not warm enough to rain (means it’s freezing)
            if (temperature < 0.0F) {
                cir.setReturnValue(false);
                cir.cancel();
            }

        }
        else if (level instanceof ClientLevel clientLevel) {
            var biomeKey = clientLevel.getBiome(pos).unwrapKey().orElse(null);
            if (biomeKey != null) {
                boolean freezing = BiomeClientTemperatureCache.isFreezing(biomeKey.location(),clientLevel);
                cir.setReturnValue(!freezing);
                cir.cancel();
            }
        }

        // Otherwise let the original logic proceed (warm enough)
    }
}

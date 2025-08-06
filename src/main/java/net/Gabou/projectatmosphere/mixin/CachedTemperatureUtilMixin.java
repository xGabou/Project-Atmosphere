package net.Gabou.projectatmosphere.mixin;

import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;

/**
 * Caches Legendary Survival Overhaul world temperature lookups so
 * subsequent requests for the same position return instantly while
 * preserving the exact values produced by the mod.
 */
@Mixin(value = TemperatureUtil.class, remap = false)
public class CachedTemperatureUtilMixin {

    private static final Map<ResourceKey<Level>, Long2FloatMap> CACHE = new HashMap<>();

    @Inject(method = "getWorldTemperature", at = @At("HEAD"), cancellable = true)
    private static void projectatmosphere$getCached(Level level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        Long2FloatMap map = CACHE.get(level.dimension());
        if (map != null) {
            long key = pos.asLong();
            if (map.containsKey(key)) {
                cir.setReturnValue(map.get(key));
            }
        }
    }

    @Inject(method = "getWorldTemperature", at = @At("RETURN"))
    private static void projectatmosphere$storeCached(Level level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        Long2FloatMap map = CACHE.computeIfAbsent(level.dimension(), d -> new Long2FloatOpenHashMap());
        map.put(pos.asLong(), cir.getReturnValue());
    }
}

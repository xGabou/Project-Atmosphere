package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.client.BiomeClientTemperatureCache;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Biome.class, remap = false)
public class BiomeFreezingMixin {
    private static final float FREEZE_THRESHOLD_C = 0.0f;

    @Inject(
            method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void projectatmosphere$shouldFreeze(LevelReader level, BlockPos pos, boolean mustBeAtEdge, CallbackInfoReturnable<Boolean> cir) {
        Float temperature = resolveTemperature(level, pos);
        if (temperature == null) {
            return;
        }
        if (temperature >= FREEZE_THRESHOLD_C) {
            cir.setReturnValue(false);
            return;
        }
        if (pos.getY() >= level.getMinBuildHeight()
                && pos.getY() < level.getMaxBuildHeight()
                && level.getBrightness(LightLayer.BLOCK, pos) < 10) {
            BlockState blockstate = level.getBlockState(pos);
            FluidState fluidstate = level.getFluidState(pos);
            if (fluidstate.getType() == Fluids.WATER && blockstate.getBlock() instanceof LiquidBlock) {
                if (!mustBeAtEdge) {
                    cir.setReturnValue(true);
                    return;
                }
                boolean surrounded = level.isWaterAt(pos.west())
                        && level.isWaterAt(pos.east())
                        && level.isWaterAt(pos.north())
                        && level.isWaterAt(pos.south());
                if (!surrounded) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
        cir.setReturnValue(false);
    }

    @Inject(
            method = "shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void projectatmosphere$shouldSnow(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Float temperature = resolveTemperature(level, pos);
        if (temperature == null) {
            return;
        }
        if (temperature >= FREEZE_THRESHOLD_C) {
            cir.setReturnValue(false);
            return;
        }
        if (pos.getY() >= level.getMinBuildHeight()
                && pos.getY() < level.getMaxBuildHeight()
                && level.getBrightness(LightLayer.BLOCK, pos) < 10) {
            BlockState blockstate = level.getBlockState(pos);
            if ((blockstate.isAir() || blockstate.is(Blocks.SNOW)) && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }

    private static Float resolveTemperature(LevelReader level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            ResourceLocation biomeId = serverLevel.getBiome(pos).unwrapKey().map(key -> key.location()).orElse(null);
            if (biomeId == null) {
                return null;
            }
            BiomeInstanceKey key = new BiomeInstanceKey(biomeId, pos);
            return ForecastOrchestrator.getCurrentTemperature(key, serverLevel.getGameTime());
        }
        if (level instanceof Level clientLevel
                && "net.minecraft.client.multiplayer.ClientLevel".equals(level.getClass().getName())) {
            ResourceLocation biomeId = clientLevel.getBiome(pos).unwrapKey().map(key -> key.location()).orElse(null);
            if (biomeId == null) {
                return null;
            }
            return BiomeClientTemperatureCache.getTemperature(biomeId, clientLevel);
        }
        return null;
    }
}

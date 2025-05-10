package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class SnowBlockGenericMixin {

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void onRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (state.is(Blocks.SNOW_BLOCK)) {

            float temp = TemperatureProfileManager.getCurrentTemperature(new BiomeInstanceKey(level.getBiome(pos).unwrapKey().get().location(), pos),level.getDayTime());
            if (temp > 0.0f) {
                level.removeBlock(pos, false);
                ci.cancel();
            }
        }
    }
}
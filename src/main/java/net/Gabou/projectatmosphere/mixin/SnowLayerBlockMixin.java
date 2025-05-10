package net.Gabou.projectatmosphere.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(SnowLayerBlock.class)
public abstract class SnowLayerBlockMixin extends Block {

    public SnowLayerBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void onRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        float temp = TemperatureManager.getRealTemperature(level, pos);
        if (temp > 0.0f) {
            level.removeBlock(pos, false);
        }
    }
}



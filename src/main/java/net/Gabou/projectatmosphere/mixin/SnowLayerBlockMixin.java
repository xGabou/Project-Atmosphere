package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
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
        float temp = TemperatureProfileManager.getCurrentTemperature(new BiomeInstanceKey(level.getBiome(pos).unwrapKey().get().location(), pos),level.getDayTime());
        ProjectAtmosphere.LOGGER.info("Temperature: " + temp+" at " + pos+" in " + level.getBiome(pos).unwrapKey().get().location()+" at " + level.getDayTime());
        if (temp > 0.0f) {
            level.removeBlock(pos, false);
            ProjectAtmosphere.LOGGER.info("Removing snow layer at " + pos + " due to temperature: " + temp);
        }
    }
}



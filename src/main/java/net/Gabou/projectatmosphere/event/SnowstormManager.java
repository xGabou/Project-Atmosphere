package net.Gabou.projectatmosphere.event;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.ModList;

public class SnowstormManager {

    private static final double ACCUMULATION_RATE_PER_TICK = 1.0 / 1200.0;

    public static void tick(ServerLevel level) {
        if (!level.isRaining()) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            Biome biome = level.getBiome(pos).value();
            if (biome.coldEnoughToSnow(pos)) {
                applyEffects(player);
                accumulateSnow(level, pos);
            }
        }
    }

    private static void applyEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
        applyFreezingCompat(player);
        int forecast = forecastBlockCount(20 * 60);
        player.displayClientMessage(Component.literal("Snow forecast: " + forecast + " blocks"), true);
    }

    private static void accumulateSnow(ServerLevel level, BlockPos pos) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        if (level.isEmptyBlock(top)) {
            level.setBlock(top, Blocks.SNOW.defaultBlockState(), 3);
        } else if (level.getBlockState(top).is(Blocks.SNOW)) {
            int layers = level.getBlockState(top).getValue(SnowLayerBlock.LAYERS);
            if (layers < 8) {
                level.setBlock(top, level.getBlockState(top).setValue(SnowLayerBlock.LAYERS, layers + 1), 3);
            }
        }
    }

    private static void applyFreezingCompat(ServerPlayer player) {
        if (ModList.get().isLoaded("toughasnails") || ModList.get().isLoaded("legendarysurvivaloverhaul") || ModList.get().isLoaded("coldsweat")) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
        }
    }

    public static int forecastBlockCount(int durationTicks) {
        return (int) (durationTicks * ACCUMULATION_RATE_PER_TICK);
    }
}

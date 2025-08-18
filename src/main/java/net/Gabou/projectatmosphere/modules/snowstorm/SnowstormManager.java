package net.Gabou.projectatmosphere.modules.snowstorm;

import com.Gabou.sereneseasonsplus.api.SnowstormHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

public class SnowstormManager {

    private static double accumulationRatePerTick = 0.0;
    private static boolean snowstormActive = false;

    public static void startSnowstorm(int intensity) {
        SnowstormHelper.startSnowstorm(intensity);
        snowstormActive = true;
        accumulationRatePerTick = intensity / 1200.0;
    }

    public static void stopSnowstorm() {
        SnowstormHelper.stopSnowstorm();
        snowstormActive = false;
        accumulationRatePerTick = 0.0;
    }

    public static void tick(ServerLevel level) {
        if (!snowstormActive) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            Biome biome = level.getBiome(pos).value();
            if (biome.coldEnoughToSnow(pos) && level.canSeeSky(pos.above())) {
                applyFreezingEffect(player);
            }
        }
    }

    private static void applyFreezingEffect(ServerPlayer player) {
        if (player.getArmorValue() > 12) {
            return;
        }

        int required = player.getTicksRequiredToFreeze();
        int current = player.getTicksFrozen();
        player.setTicksFrozen(Math.min(required - 1, current + 1));
    }

    public static int forecastBlockCount(int durationTicks) {
        return (int) (durationTicks * accumulationRatePerTick);
    }
}

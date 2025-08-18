package net.Gabou.projectatmosphere.modules.snowstorm;

import com.Gabou.sereneseasonsplus.util.SnowstormHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.fml.ModList;

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

        int forecast = forecastBlockCount(20 * 60);

        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            Biome biome = level.getBiome(pos).value();
            if (biome.coldEnoughToSnow(pos)) {
                applyEffects(player, forecast);
            }
        }
    }

    private static void applyEffects(ServerPlayer player, int forecast) {
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
        triggerTemperatureEffect(player);
        player.displayClientMessage(Component.literal("Snow forecast: " + forecast + " blocks"), true);
    }

    private static final String[] TEMPERATURE_MODS = {
            "toughasnails",
            "legendarysurvivaloverhaul",
            "coldsweat"
    };

    private static boolean isTemperatureModLoaded() {
        for (String mod : TEMPERATURE_MODS) {
            if (ModList.get().isLoaded(mod)) {
                return true;
            }
        }
        return false;
    }

    private static void triggerTemperatureEffect(ServerPlayer player) {
        if (isTemperatureModLoaded()) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
        }
    }

    public static int forecastBlockCount(int durationTicks) {
        return (int) (durationTicks * accumulationRatePerTick);
    }
}

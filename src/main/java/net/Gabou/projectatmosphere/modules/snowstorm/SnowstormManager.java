package net.Gabou.projectatmosphere.modules.snowstorm;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

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
                int intensity = forecastBlockCount(20 * 60);
                applyEffects(player, intensity);
                updateSnowstormConfig(intensity);
            }
        }
    }

    private static void applyEffects(ServerPlayer player, int intensity) {
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
        triggerTemperatureEffect(player);
        player.displayClientMessage(Component.literal("Snow forecast: " + intensity + " blocks"), true);
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

    private static void updateSnowstormConfig(int intensity) {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("sereneseasonsplus-common.toml");
        try (CommentedFileConfig config = CommentedFileConfig.builder(configPath).sync().build()) {
            config.load();
            config.set("snowstorm.enabled", true);
            config.set("snowstorm.intensity", intensity);
            config.save();
        } catch (Exception e) {
            ProjectAtmosphere.LOGGER.warn("Failed to update Serene Seasons Plus config: {}", e.getMessage());
        }

        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
    }

    public static int forecastBlockCount(int durationTicks) {
        return (int) (durationTicks * ACCUMULATION_RATE_PER_TICK);
    }
}

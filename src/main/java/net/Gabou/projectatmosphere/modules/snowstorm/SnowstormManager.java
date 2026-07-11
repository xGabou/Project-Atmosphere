package net.Gabou.projectatmosphere.modules.snowstorm;

import net.Gabou.projectatmosphere.modules.weather.SnowTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
public class SnowstormManager {



    private static final List<SnowStorm> affectedRegions = new ArrayList<>();


    public static void startSnowstorm(int intensity, double centerX, double centerZ, double radius) {
        affectedRegions.add(new SnowStorm(intensity, centerX, centerZ, radius));

    }

    public static void stopSnowstorm(SnowStorm snowstorm) {
        affectedRegions.remove(snowstorm);
    }

    public static int getSnowStormIntensity(ChunkPos pos)
    {
        return affectedRegions.stream()
                .filter(storm -> storm.intersects(pos))
                .mapToInt(SnowStorm::getIntensity)
                .max()
                .orElse(0);
    }

    public static SnowTier getSnowTier(ChunkPos pos) {
        return affectedRegions.stream()
                .filter(storm -> storm.intersects(pos))
                .map(SnowStorm::getTier)
                .max(java.util.Comparator.comparingInt(Enum::ordinal))
                .orElse(SnowTier.NONE);
    }


    public static boolean isSnowStormAt(ChunkPos pos){
        return affectedRegions.stream().anyMatch(storm -> storm.intersects(pos));
    }

    public static void tick(ServerLevel level) {
        for (SnowStorm snow : affectedRegions) {
            for (ServerPlayer player : level.players()) {
                BlockPos pos = player.blockPosition();
                if (snow.intersects(pos.getX() - 5.0D, pos.getZ() - 5.0D, pos.getX() + 5.0D, pos.getZ() + 5.0D))
                    applyEffects(player, snow);

            }
        }
    }





    private static void applyEffects(ServerPlayer player, SnowStorm snowstorm) {
        SnowTier tier = snowstorm.getTier();
        if (tier == SnowTier.NONE) {
            return;
        }

        int amplifier = tier == SnowTier.BLIZZARD ? 1 : 0;
        int duration = tier == SnowTier.SNOWY_DAY ? 20 : 40;
        if (tier != SnowTier.SNOWY_DAY) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, amplifier, false, false));
        }
        triggerTemperatureEffect(player);
        player.displayClientMessage(Component.literal("Snow tier: " + tier.name() + " (" + snowstorm.getIntensity() + ")"), true);
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
            if(player.getArmorValue()>12)//TODO : make this configurable
                return;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
        }
    }


}

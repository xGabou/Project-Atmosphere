package net.Gabou.projectatmosphere.modules.snowstorm;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class SnowstormManager {



    private static final List<SnowStorm> affectedRegions = new ArrayList<>();


    public static void startSnowstorm(int intensity,CloudRegion region) {
        affectedRegions.add(new SnowStorm(intensity,region));

    }

    public static void stopSnowstorm(SnowStorm snowstorm) {
        affectedRegions.remove(snowstorm);
    }

    public static int getSnowStormIntensity(ChunkPos pos)
    {
        SpawnRegion region = new SpawnRegion(pos.getMaxBlockX(),pos.getMinBlockZ(),16);
        return affectedRegions.stream()
                .filter(storm -> storm.getCloudRegion().intersects(region))
                .mapToInt(SnowStorm::getIntensity)
                .max()
                .orElse(0);
    }


    public static boolean isSnowStormAt(ChunkPos pos){
        SpawnRegion region = new SpawnRegion(pos.getMaxBlockX(),pos.getMinBlockZ(),16);
        return affectedRegions.stream().anyMatch(storm -> storm.getCloudRegion().intersects(region));
    }

    public static void tick(ServerLevel level) {
        for (SnowStorm snow : affectedRegions) {
            for (ServerPlayer player : level.players()) {
                BlockPos pos = player.blockPosition();
                if (snow.getCloudRegion().intersects(new SpawnRegion(pos.getX(), pos.getY(), 5)))
                    applyEffects(player, snow.getIntensity());

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
            if(player.getArmorValue()>12)//TODO : make this configurable
                return;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
        }
    }


}

package net.Gabou.projectatmosphere.event;

import com.BreadRes.desertstormwarming.sounds.SandstormSounds;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastDataStorage;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.sandStorm.SandStormAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class BiomeChangeManager {
    private static final Map<UUID, Pair<ResourceLocation, Boolean>> lastBiome = new HashMap<>();
    private static final int RUN_INTERVAL_TICKS = 2000;
    private static final int MIN_DISTANCE_BETWEEN_CENTERS = 6000;


    public static  Map<UUID, Pair<ResourceLocation, Boolean>> getLastBiome() {
        return lastBiome;
    }



    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        if (!(ev.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        ServerLevel level = player.serverLevel();
        long t = player.serverLevel().getDayTime() % 24000L;
        if (t % RUN_INTERVAL_TICKS != 0) return;

        UUID uuid = player.getUUID();
        ResourceLocation nowBiome = getBiomeKeyAt(player);
        ResourceLocation last;
        boolean wasInDesert;
        try{
            last = lastBiome.get(uuid).getKey();
            wasInDesert = lastBiome.get(uuid).getValue();
        }
        catch (NullPointerException e){
            last = null;
            wasInDesert = false;
        }

        if(!wasInDesert) {
            if(SandStormAPI.isSandstormActive()) {
                for (SoundEvent soundEvent : SandstormSounds.getSoundsForPhase(SandStormAPI.getSandstormPhase())) {
                    Minecraft.getInstance().getSoundManager().stop(soundEvent.getLocation(),null);
                }

            }
        }

        if (last == null || !last.equals(nowBiome)) {
            lastBiome.put(uuid,Pair.of(nowBiome,isDesert(level,nowBiome)));
            onBiomeChanged(player, last, nowBiome); 
        }

    }
    public static boolean isDesert(Level level, ResourceLocation biomeId)
    {
        return level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, biomeId))
                .map(holder -> holder.is(Tags.Biomes.IS_DESERT))
                .orElse(false);
    }


    private static ResourceLocation getBiomeKeyAt(ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        return p.serverLevel()
                .getBiome(pos)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(ResourceLocation.fromNamespaceAndPath("minecraft", "plains"));
    }

    private static void onBiomeChanged(ServerPlayer player, ResourceLocation oldBiome, ResourceLocation newBiome) {
        UUID uuid = player.getUUID();
        BlockPos currentPos = player.blockPosition();
        BlockPos originalCenter = ForecastDataStorage.playerData.get(uuid);

        
        if (originalCenter == null || originalCenter.distManhattan(currentPos) > MIN_DISTANCE_BETWEEN_CENTERS) {
            ForecastDataStorage.playerData.put(uuid, currentPos); 
            AtmosphereManager.updateForecastAround(player.serverLevel(), currentPos);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[Atmosphere] Moved >5000 blocks. Forecast regenerated."
            ));
        }
    }
}

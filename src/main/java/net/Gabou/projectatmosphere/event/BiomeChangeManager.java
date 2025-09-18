package net.Gabou.projectatmosphere.event;


import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastDataStorage;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
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

import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class BiomeChangeManager {
    private static final Map<UUID, Pair<ResourceLocation, Boolean>> lastBiome = new HashMap<>();
    private static final int RUN_INTERVAL_TICKS = 2000;
    private static final int MIN_DISTANCE_BETWEEN_CENTERS = 6000;

    private static final boolean sandStormsLoaded = CompatHandler.isSandStormsLoaded();


    public static  Map<UUID, Pair<ResourceLocation, Boolean>> getLastBiome() {
        return lastBiome;
    }



    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post ev) {
        if (!(ev.getEntity() instanceof ServerPlayer player)) return;
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

        if (last == null || !last.equals(nowBiome)) {
            lastBiome.put(uuid,Pair.of(nowBiome,isDesert(nowBiome)));
            onBiomeChanged(player, last, nowBiome); 
        }

    }
    public static boolean isDesert(ResourceLocation biomeId)
    {
        return isSandstormBiome(biomeId);
    }
    private static final Set<String> SANDSTORM_KEYWORDS = Set.of(
            "desert", "badlands", "mesa", "wasteland",
            "volcanic_plains", "lush_desert", "cold_desert",
            "dryland", "scrubland", "shrubland", "rocky_shrubland",
            "tundra", "dead_forest", "old_growth_dead_forest",
            "arid", "savanna_badlands", "red_desert",
            "ash", "barren", "dry"
    );

    private static boolean isSandstormBiome(ResourceLocation biomeKey) {
        if (biomeKey == null) {
            return false;
        }
        String path = biomeKey.getPath().toLowerCase();
        for (String keyword : SANDSTORM_KEYWORDS) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        return false;
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

        ForecastOrchestrator.clearActiveBiomeKeysForPlayer(player);
        ForecastOrchestrator.getNearbyBiomeKeys(player.serverLevel(), player, 500);

        if (originalCenter == null || originalCenter.distManhattan(currentPos) > MIN_DISTANCE_BETWEEN_CENTERS) {
            ForecastDataStorage.playerData.put(uuid, currentPos);
            AtmosphereManager.updateForecastAround(player.serverLevel(), currentPos);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[Atmosphere] Moved >5000 blocks. Forecast regenerated."
            ));
        }
    }
}

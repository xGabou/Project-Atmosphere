package net.Gabou.projectatmosphere.event;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastDataStorage;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class BiomeChangeManager {
    private static final Map<UUID, RegionTrack> regionTrack = new HashMap<>();
    private static final Map<UUID, Pair<ResourceLocation, Boolean>> lastBiome = new HashMap<>();
    private static final int RUN_INTERVAL_TICKS = 2000;
    // Threshold to move the tracked center: 4/5 of default region size.
    private static final int MOVE_THRESHOLD = (int) (ProjectAtmosphere.DEFAULT_RADIUS * 0.8);


    private static final boolean sandStormsLoaded = CompatHandler.isSandStormsLoaded();
    public static  Map<UUID, Pair<ResourceLocation, Boolean>> getLastBiome() {
        return lastBiome;
    }



    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END||ev.side.isClient()) return;
        if (!(ev.player instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        long t = player.serverLevel().getDayTime() % 24000L;
        if (t % RUN_INTERVAL_TICKS != 0) return;

        UUID uuid = player.getUUID();
        BlockPos pos = player.blockPosition();
        RegionInstanceKey region = RegionInstanceKey.from(pos);
        RegionTrack track = regionTrack.computeIfAbsent(uuid, id -> new RegionTrack(region, pos));
        ResourceLocation nowBiome = getBiomeKeyAt(player);
        Pair<ResourceLocation, Boolean> last = lastBiome.get(uuid);
        if (last == null || !last.getKey().equals(nowBiome)) {
            lastBiome.put(uuid, Pair.of(nowBiome, isDesert(nowBiome)));
        }
        // If we enter a new region, update and trigger regen.
        if (!track.region().equals(region)) {
            track = new RegionTrack(region, pos);
            regionTrack.put(uuid, track);
            onRegionChanged(player, region, nowBiome);
            return;
        }
        // If we moved far from the tracked center within the same region, update center and regen.
        if (track.center().distManhattan(pos) > MOVE_THRESHOLD) {
            track = new RegionTrack(region, pos);
            regionTrack.put(uuid, track);
            onRegionChanged(player, region, nowBiome);
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

    private static void onRegionChanged(ServerPlayer player, RegionInstanceKey region, ResourceLocation currentBiome) {
        UUID uuid = player.getUUID();
        BlockPos currentPos = player.blockPosition();
        ForecastDataStorage.playerData.put(uuid, currentPos);
        lastBiome.put(uuid, Pair.of(currentBiome, isDesert(currentBiome)));
        ForecastOrchestrator.clearActiveRegionsForPlayer(player);
        ForecastOrchestrator.getNearbyRegions(player.serverLevel(), player, 500);
        AtmosphereManager.updateForecastAround(player.serverLevel(), currentPos);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[Atmosphere] Entered region " + region + ". Forecast regenerated."
        ));
    }

    private record RegionTrack(RegionInstanceKey region, BlockPos center) {}
}

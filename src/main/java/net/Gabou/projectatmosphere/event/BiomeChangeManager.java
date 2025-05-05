package net.Gabou.projectatmosphere.event;

import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class BiomeChangeManager {
    // track each player’s last biome
    private static final Map<UUID, ResourceLocation> lastBiome = new HashMap<>();
    private static final int RUN_INTERVAL_TICKS = 500;

    /** initialize when they join */
    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation biomeKey = getBiomeKeyAt(player);
        lastBiome.put(player.getUUID(), biomeKey);
    }

    /** on each tick, check if they’ve moved into a new biome */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        if (!(ev.player instanceof ServerPlayer player)) return;
        if(player.level().isClientSide) return;
        long t = player.serverLevel().getDayTime() % 24000L;
        if (t % RUN_INTERVAL_TICKS != 0) return;


        ResourceLocation prev = lastBiome.get(player.getUUID());
        ResourceLocation now  = getBiomeKeyAt(player);
        if (prev == null || !now.equals(prev)) {
            // they’ve changed biomes!
            lastBiome.put(player.getUUID(), now);
            onBiomeChanged(player, prev, now);
        }

    }

    private static ResourceLocation getBiomeKeyAt(ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        return p.serverLevel()
                .getBiome(pos)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(new ResourceLocation("minecraft","plains"));
    }

    /** your callback: do whatever you need when biome changes */
    private static void onBiomeChanged(ServerPlayer player, ResourceLocation oldBiome, ResourceLocation newBiome) {
        // e.g. reload low-detail forecast around them:
        // PressureForecast.generateLowDetailForecast(player.getLevel(), player.blockPosition(), YOUR_RADIUS);
        // PressureForecast.deactivateFarthestBiome(player.getLevel());
        AtmosphereManager.updateForecastAround(player.serverLevel(), player.blockPosition());
        player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                        "You moved from " + oldBiome + " → " + newBiome
                )
        );
    }
}

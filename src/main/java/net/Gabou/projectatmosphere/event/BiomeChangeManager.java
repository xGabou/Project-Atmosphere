package net.Gabou.projectatmosphere.event;

import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastDataStorage;
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
    private static final Map<UUID, ResourceLocation> lastBiome = new HashMap<>();
    private static final int RUN_INTERVAL_TICKS = 2000;
    private static final int MIN_DISTANCE_BETWEEN_CENTERS = 6000;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        if (!(ev.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        long t = player.serverLevel().getDayTime() % 24000L;
        if (t % RUN_INTERVAL_TICKS != 0) return;

        UUID uuid = player.getUUID();
        ResourceLocation nowBiome = getBiomeKeyAt(player);
        ResourceLocation last = lastBiome.get(uuid);

        if (last == null || !last.equals(nowBiome)) {
            lastBiome.put(uuid, nowBiome);
            onBiomeChanged(player, last, nowBiome); // decision to regen is now inside
        }
    }

    private static ResourceLocation getBiomeKeyAt(ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        return p.serverLevel()
                .getBiome(pos)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(new ResourceLocation("minecraft", "plains"));
    }

    private static void onBiomeChanged(ServerPlayer player, ResourceLocation oldBiome, ResourceLocation newBiome) {
        UUID uuid = player.getUUID();
        BlockPos currentPos = player.blockPosition();
        BlockPos originalCenter = ForecastDataStorage.playerData.get(uuid);

        // only trigger update if the player moved far from original forecast center
        if (originalCenter == null || originalCenter.distManhattan(currentPos) > MIN_DISTANCE_BETWEEN_CENTERS) {
            ForecastDataStorage.playerData.put(uuid, currentPos); // update center
            AtmosphereManager.updateForecastAround(player.serverLevel(), currentPos);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[Atmosphere] Moved >5000 blocks. Forecast regenerated."
            ));
        }
    }
}

package net.Gabou.projectatmosphere.modules.seasonaltrees;

import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeasonalTreesCore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class SeasonalTreesEventHandler {
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) {
            return;
        }
        SeasonalTreesCore.onChunkLoaded(level, chunk.getPos());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos chunkPos = event.getChunk().getPos();
        SeasonalTreesCore.onChunkUnloaded(level, chunkPos);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        SeasonalTreesCore.tick(level);
    }
}

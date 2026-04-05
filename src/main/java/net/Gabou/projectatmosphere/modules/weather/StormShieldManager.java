package net.Gabou.projectatmosphere.modules.weather;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public final class StormShieldManager {
    public static final double PROTECTION_RADIUS = 96.0D;
    private static final double PROTECTION_RADIUS_SQ = PROTECTION_RADIUS * PROTECTION_RADIUS;
    private static final Map<String, Set<Long>> SHIELDS_BY_LEVEL = new ConcurrentHashMap<>();

    private StormShieldManager() {
    }

    public static void register(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        SHIELDS_BY_LEVEL.computeIfAbsent(level.dimension().location().toString(), unused -> ConcurrentHashMap.newKeySet())
                .add(pos.asLong());
    }

    public static void unregister(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        Set<Long> positions = SHIELDS_BY_LEVEL.get(level.dimension().location().toString());
        if (positions != null) {
            positions.remove(pos.asLong());
        }
    }

    public static boolean isProtected(Level level, Vec3 pos) {
        return getMaxProtection(level, pos, 0.0D) > 0.0D;
    }

    public static boolean isProtected(Level level, BlockPos pos) {
        return isProtected(level, Vec3.atCenterOf(pos));
    }

    public static Vec3 sampleAvoidance(ServerLevel level, Vec3 pos, double margin) {
        Set<Long> positions = SHIELDS_BY_LEVEL.get(level.dimension().location().toString());
        if (positions == null || positions.isEmpty()) {
            return Vec3.ZERO;
        }

        double influenceRadius = PROTECTION_RADIUS + Math.max(0.0D, margin);
        double influenceRadiusSq = influenceRadius * influenceRadius;
        Vec3 accumulated = Vec3.ZERO;

        for (long packed : positions) {
            double shieldX = BlockPos.getX(packed) + 0.5D;
            double shieldY = BlockPos.getY(packed) + 0.5D;
            double shieldZ = BlockPos.getZ(packed) + 0.5D;
            Vec3 away = pos.subtract(shieldX, shieldY, shieldZ);
            double distSq = away.lengthSqr();
            if (distSq > influenceRadiusSq || distSq < 1.0E-4D) {
                continue;
            }

            double dist = Math.sqrt(distSq);
            double strength = 1.0D - dist / influenceRadius;
            accumulated = accumulated.add(away.normalize().scale(strength));
        }

        if (accumulated.lengthSqr() < 1.0E-4D) {
            return Vec3.ZERO;
        }
        return accumulated.normalize().scale(Math.min(accumulated.length(), 1.0D));
    }

    public static double getMaxProtection(Level level, Vec3 pos, double margin) {
        Set<Long> positions = SHIELDS_BY_LEVEL.get(level.dimension().location().toString());
        if (positions == null || positions.isEmpty()) {
            return 0.0D;
        }

        double influenceRadius = PROTECTION_RADIUS + Math.max(0.0D, margin);
        double influenceRadiusSq = influenceRadius * influenceRadius;
        double strongest = 0.0D;

        for (long packed : positions) {
            double shieldX = BlockPos.getX(packed) + 0.5D;
            double shieldY = BlockPos.getY(packed) + 0.5D;
            double shieldZ = BlockPos.getZ(packed) + 0.5D;
            double distSq = pos.distanceToSqr(shieldX, shieldY, shieldZ);
            if (distSq > influenceRadiusSq) {
                continue;
            }

            double dist = Math.sqrt(distSq);
            strongest = Math.max(strongest, 1.0D - dist / influenceRadius);
        }
        return strongest;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        scanChunk(level, chunk);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Set<Long> positions = SHIELDS_BY_LEVEL.get(level.dimension().location().toString());
        if (positions == null || positions.isEmpty()) {
            return;
        }

        ChunkPos chunkPos = event.getChunk().getPos();
        positions.removeIf(packed -> SectionPosOf(packed, true) == chunkPos.x && SectionPosOf(packed, false) == chunkPos.z);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            SHIELDS_BY_LEVEL.remove(level.dimension().location().toString());
        }
    }

    private static void scanChunk(ServerLevel level, LevelChunk chunk) {
        Set<Long> positions = SHIELDS_BY_LEVEL.computeIfAbsent(level.dimension().location().toString(), unused -> ConcurrentHashMap.newKeySet());
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    cursor.set(x, y, z);
                    BlockState state = chunk.getBlockState(cursor);
                    if (state.is(ModBlocks.STORM_SHIELD.get())) {
                        positions.add(cursor.asLong());
                    }
                }
            }
        }
    }

    private static int SectionPosOf(long packed, boolean xAxis) {
        return xAxis ? BlockPos.getX(packed) >> 4 : BlockPos.getZ(packed) >> 4;
    }
}

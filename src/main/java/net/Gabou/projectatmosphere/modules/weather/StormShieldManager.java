package net.Gabou.projectatmosphere.modules.weather;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public final class StormShieldManager {
    public static final double PROTECTION_RADIUS = 96.0D;

    private static final ResourceLocation STORM_SHIELD_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "storm_shield");

    private static final Map<String, ShieldIndex> SHIELDS_BY_LEVEL = new HashMap<>();

    private StormShieldManager() {
    }

    public static void register(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }

        ShieldIndex index = getOrCreateIndex(level);
        index.add(chunkKey(pos), pos.asLong());
    }

    public static void unregister(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }

        String levelKey = level.dimension().location().toString();
        ShieldIndex index = SHIELDS_BY_LEVEL.get(levelKey);
        if (index == null) {
            return;
        }

        index.remove(chunkKey(pos), pos.asLong());

        if (index.isEmpty()) {
            SHIELDS_BY_LEVEL.remove(levelKey);
        }
    }

    public static boolean isProtected(Level level, Vec3 pos) {
        return getMaxProtection(level, pos, 0.0D) > 0.0D;
    }

    public static boolean isProtected(Level level, BlockPos pos) {
        return isProtected(level, Vec3.atCenterOf(pos));
    }

    public static Vec3 sampleAvoidance(ServerLevel level, Vec3 pos, double margin) {
        ShieldIndex index = SHIELDS_BY_LEVEL.get(level.dimension().location().toString());
        if (index == null || index.isEmpty()) {
            return Vec3.ZERO;
        }

        double influenceRadius = PROTECTION_RADIUS + Math.max(0.0D, margin);
        double influenceRadiusSq = influenceRadius * influenceRadius;
        double[] accumulated = new double[3];

        index.forEachNearby(pos, influenceRadius, packed -> {
            double shieldX = BlockPos.getX(packed) + 0.5D;
            double shieldY = BlockPos.getY(packed) + 0.5D;
            double shieldZ = BlockPos.getZ(packed) + 0.5D;

            Vec3 away = pos.subtract(shieldX, shieldY, shieldZ);
            double distSq = away.lengthSqr();

            if (distSq > influenceRadiusSq || distSq < 1.0E-4D) {
                return;
            }

            double dist = Math.sqrt(distSq);
            double strength = 1.0D - dist / influenceRadius;
            Vec3 weighted = away.normalize().scale(strength);

            accumulated[0] += weighted.x;
            accumulated[1] += weighted.y;
            accumulated[2] += weighted.z;
        });

        Vec3 result = new Vec3(accumulated[0], accumulated[1], accumulated[2]);

        if (result.lengthSqr() < 1.0E-4D) {
            return Vec3.ZERO;
        }

        return result.normalize().scale(Math.min(result.length(), 1.0D));
    }

    public static double getMaxProtection(Level level, Vec3 pos, double margin) {
        ShieldIndex index = SHIELDS_BY_LEVEL.get(level.dimension().location().toString());
        if (index == null || index.isEmpty()) {
            return 0.0D;
        }

        double influenceRadius = PROTECTION_RADIUS + Math.max(0.0D, margin);
        double influenceRadiusSq = influenceRadius * influenceRadius;
        double[] strongest = {0.0D};

        index.forEachNearby(pos, influenceRadius, packed -> {
            double shieldX = BlockPos.getX(packed) + 0.5D;
            double shieldY = BlockPos.getY(packed) + 0.5D;
            double shieldZ = BlockPos.getZ(packed) + 0.5D;

            double distSq = pos.distanceToSqr(shieldX, shieldY, shieldZ);
            if (distSq > influenceRadiusSq) {
                return;
            }

            double dist = Math.sqrt(distSq);
            strongest[0] = Math.max(strongest[0], 1.0D - dist / influenceRadius);
        });

        return strongest[0];
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (isStormShield(event.getPlacedBlock())) {
            register(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockState state = level.getBlockState(event.getPos());

        if (isStormShield(state)) {
            unregister(level, event.getPos());
        }
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

        String levelKey = level.dimension().location().toString();
        ShieldIndex index = SHIELDS_BY_LEVEL.get(levelKey);

        if (index == null || index.isEmpty()) {
            return;
        }

        index.removeChunk(event.getChunk().getPos().toLong());

        if (index.isEmpty()) {
            SHIELDS_BY_LEVEL.remove(levelKey);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            SHIELDS_BY_LEVEL.remove(level.dimension().location().toString());
        }
    }

    private static void scanChunk(ServerLevel level, LevelChunk chunk) {
        Block stormShield = getStormShieldBlock();
        if (stormShield == null) {
            return;
        }

        ShieldIndex index = getOrCreateIndex(level);
        long chunkKey = chunk.getPos().toLong();

        LongArrayList foundPositions = new LongArrayList(2);
        LevelChunkSection[] sections = chunk.getSections();

        int minSection = level.getMinSection();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];

            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> state.is(stormShield))) {
                continue;
            }

            int sectionMinY = (minSection + sectionIndex) << 4;

            for (int localY = 0; localY < 16; localY++) {
                int worldY = sectionMinY + localY;

                for (int localZ = 0; localZ < 16; localZ++) {
                    int worldZ = minZ + localZ;

                    for (int localX = 0; localX < 16; localX++) {
                        if (!section.getBlockState(localX, localY, localZ).is(stormShield)) {
                            continue;
                        }

                        cursor.set(minX + localX, worldY, worldZ);
                        foundPositions.add(cursor.asLong());
                    }
                }
            }
        }

        index.replaceChunk(chunkKey, foundPositions);

        if (index.isEmpty()) {
            SHIELDS_BY_LEVEL.remove(level.dimension().location().toString());
        }
    }

    private static ShieldIndex getOrCreateIndex(Level level) {
        return SHIELDS_BY_LEVEL.computeIfAbsent(level.dimension().location().toString(), unused -> new ShieldIndex());
    }

    private static long chunkKey(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static boolean isStormShield(BlockState state) {
        Block stormShield = getStormShieldBlock();
        return stormShield != null && state.is(stormShield);
    }

    private static Block getStormShieldBlock() {
        return ForgeRegistries.BLOCKS.getValue(STORM_SHIELD_ID);
    }

    private static final class ShieldIndex {
        private final Long2ObjectOpenHashMap<LongArrayList> positionsByChunk = new Long2ObjectOpenHashMap<>();
        private int size;

        boolean isEmpty() {
            return this.size == 0;
        }

        void add(long chunkKey, long packedPos) {
            LongArrayList positions = this.positionsByChunk.computeIfAbsent(chunkKey, unused -> new LongArrayList(1));

            for (int i = 0; i < positions.size(); i++) {
                if (positions.getLong(i) == packedPos) {
                    return;
                }
            }

            positions.add(packedPos);
            this.size++;
        }

        void remove(long chunkKey, long packedPos) {
            LongArrayList positions = this.positionsByChunk.get(chunkKey);
            if (positions == null) {
                return;
            }

            for (int i = 0; i < positions.size(); i++) {
                if (positions.getLong(i) != packedPos) {
                    continue;
                }

                positions.removeLong(i);
                this.size--;

                if (positions.isEmpty()) {
                    this.positionsByChunk.remove(chunkKey);
                }

                return;
            }
        }

        void replaceChunk(long chunkKey, LongArrayList newPositions) {
            LongArrayList previous = this.positionsByChunk.remove(chunkKey);

            if (previous != null) {
                this.size -= previous.size();
            }

            if (newPositions == null || newPositions.isEmpty()) {
                return;
            }

            this.positionsByChunk.put(chunkKey, newPositions);
            this.size += newPositions.size();
        }

        void removeChunk(long chunkKey) {
            LongArrayList removed = this.positionsByChunk.remove(chunkKey);

            if (removed != null) {
                this.size -= removed.size();
            }
        }

        void forEachNearby(Vec3 pos, double radius, LongConsumer consumer) {
            int minChunkX = Mth.floor((pos.x - radius) / 16.0D);
            int maxChunkX = Mth.floor((pos.x + radius) / 16.0D);
            int minChunkZ = Mth.floor((pos.z - radius) / 16.0D);
            int maxChunkZ = Mth.floor((pos.z + radius) / 16.0D);

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    LongArrayList positions = this.positionsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));

                    if (positions == null || positions.isEmpty()) {
                        continue;
                    }

                    for (int i = 0; i < positions.size(); i++) {
                        consumer.accept(positions.getLong(i));
                    }
                }
            }
        }
    }
}
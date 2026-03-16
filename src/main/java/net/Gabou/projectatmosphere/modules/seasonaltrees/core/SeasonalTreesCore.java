package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.seasonaltrees.data.SeasonalTreesSavedData;
import net.Gabou.projectatmosphere.modules.seasonaltrees.integration.SeasonalTreesAccessorRegistry;
import net.Gabou.projectatmosphere.modules.seasonaltrees.integration.SeasonalTreesTreeAccessor;
import net.Gabou.projectatmosphere.modules.seasonaltrees.transport.SeasonalTreesSeedTransport;
import net.Gabou.projectatmosphere.modules.seasonaltrees.transport.WindSeedTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SeasonalTreesCore {
    private static final Map<String, SeasonalTreesRuntime> RUNTIMES = new HashMap<>();
    private static final SeasonalTreesAccessorRegistry ACCESSORS = new SeasonalTreesAccessorRegistry();
    private static final SeasonalTreesSeasonProvider SEASON_PROVIDER = new SeasonalTreesPaSeasonProvider();
    private static final SeasonalTreesSeedTransport WIND_TRANSPORT = new WindSeedTransport();
    private static final List<SeasonalTreesVigorProvider> VIGOR_PROVIDERS = new ArrayList<>();
    private static boolean VIGOR_PROVIDER_REGISTERED = false;

    private SeasonalTreesCore() {
    }

    public static void registerReadOnlyEnhancements() {
        if (VIGOR_PROVIDER_REGISTERED) {
            return;
        }
        VIGOR_PROVIDER_REGISTERED = true;
        VIGOR_PROVIDERS.add(new PaClimateVigorProvider());
    }

    public static void onChunkLoaded(ServerLevel level, ChunkPos chunkPos) {
        SeasonalTreesRuntime runtime = runtime(level);
        runtime.onChunkLoaded(chunkPos);
    }

    public static void onChunkUnloaded(ServerLevel level, ChunkPos chunkPos) {
        SeasonalTreesRuntime runtime = runtime(level);
        runtime.onChunkUnloaded(chunkPos);
    }

    public static void tick(ServerLevel level) {
        if (!AtmoCommonConfig.SEASONAL_TREES_ENABLED.get()) {
            return;
        }
        runtime(level).tick();
    }

    public static boolean tryPlantSeed(ServerLevel level, SeedPayload payload) {
        int radius = AtmoCommonConfig.SEASONAL_TREES_SPREAD_RADIUS_BLOCKS.get();
        RandomSource random = level.getRandom();
        int dx = random.nextInt(radius * 2 + 1) - radius;
        int dz = random.nextInt(radius * 2 + 1) - radius;
        BlockPos target = payload.sourcePos().offset(dx, 0, dz);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target);
        return tryPlantSeedAt(level, surface, payload);
    }

    public static boolean tryPlantSeedAt(ServerLevel level, BlockPos pos, SeedPayload payload) {
        if (pos == null || payload == null) {
            return false;
        }
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockPos ground = pos.below();
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        if (!level.getBlockState(ground).isSolid()) {
            return false;
        }
        int light = level.getMaxLocalRawBrightness(pos);
        if (light < 9) {
            return false;
        }
        SeasonalTreesTreeAccessor accessor = ACCESSORS.getAccessor(payload.treeType());
        if (accessor == null || !accessor.isEnabled()) {
            return false;
        }
        return accessor.plantSeed(level, pos, payload.speciesId());
    }

    private static SeasonalTreesRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(level.dimension().location().toString(), key -> new SeasonalTreesRuntime(level));
    }

    private static final class SeasonalTreesRuntime {
        private final ServerLevel level;
        private final SeasonalTreesSavedData data;
        private final Set<Long> loadedChunks = new HashSet<>();
        private final Deque<ChunkPos> scanQueue = new ArrayDeque<>();
        private final Map<Long, ChunkScanCursor> scanCursors = new HashMap<>();
        private final Deque<TreeRecord> processingQueue = new ArrayDeque<>();

        private SeasonalTreesRuntime(ServerLevel level) {
            this.level = level;
            this.data = SeasonalTreesSavedData.get(level);
        }

        private void onChunkLoaded(ChunkPos chunkPos) {
            long key = chunkPos.toLong();
            if (loadedChunks.add(key)) {
                scanQueue.add(chunkPos);
                for (TreeRecord record : data.getTreesInChunk(chunkPos)) {
                    processingQueue.add(record);
                }
            }
        }

        private void onChunkUnloaded(ChunkPos chunkPos) {
            long key = chunkPos.toLong();
            loadedChunks.remove(key);
            scanQueue.remove(chunkPos);
            scanCursors.remove(key);
        }

        private void tick() {
            int scanBudget = AtmoCommonConfig.SEASONAL_TREES_SCAN_BUDGET_PER_TICK.get();
            runScanning(scanBudget);
            if (WIND_TRANSPORT.isEnabled()) {
                WIND_TRANSPORT.tick(level);
            }
            int budget = AtmoCommonConfig.SEASONAL_TREES_BUDGET_PER_TICK.get();
            int processed = 0;
            while (processed < budget) {
                TreeRecord record = processingQueue.poll();
                if (record == null) {
                    break;
                }
                if (!loadedChunks.contains(record.key().chunkPos().toLong())) {
                    continue;
                }
                SeasonalTreesTreeAccessor accessor = ACCESSORS.getAccessor(record.key().treeType());
                if (accessor == null || !accessor.isEnabled()) {
                    continue;
                }
                if (!accessor.isTreeValid(level, record.key())) {
                    data.removeTree(record.key());
                    continue;
                }
                boolean dirty = updateTree(record);
                if (dirty) {
                    data.putTree(record);
                    accessor.applyLeafState(level, record, SEASON_PROVIDER.getPhase(level));
                }
                trySpread(record, accessor);
                processingQueue.add(record);
                processed++;
            }
        }

        private boolean updateTree(TreeRecord record) {
            TreeState state = record.state();
            SeasonPhase phase = SEASON_PROVIDER.getPhase(level);
            long now = level.getGameTime();
            boolean dirty = false;

            long dayDuration = Math.max(1L, net.Gabou.projectatmosphere.seasons.SeasonTimeHelper.dayDuration(level));
            long currentDay = now / dayDuration;
            if (state.lastVigorDay() != currentDay) {
                Double v = AtmoCommonConfig.SEASONAL_TREES_VIGOR_REGEN_PER_DAY.get();
                float vigorGain = v == null ? 0.0f : v.floatValue();
                vigorGain *= seasonVigorFactor(phase);
                vigorGain *= getVigorMultiplier(level, record.key().rootPos());
                state.setVigor(Mth.clamp(state.vigor() + vigorGain, 0.0f, 1.0f));
                state.setLastVigorDay(currentDay);
                dirty = true;
            }

            long cooldownTicks = (long) (AtmoCommonConfig.SEASONAL_TREES_TRANSITION_COOLDOWN_DAYS.get() * dayDuration);
            long offsetTicks = computeOffsetTicks(phase, dayDuration, record.key().rootPos());
            if (phase == SeasonPhase.AUTUMN || phase == SeasonPhase.SPRING) {
                if (state.lastSeasonApplied() != phase && now - state.lastSeasonTick() >= cooldownTicks) {
                    state.setLastSeasonApplied(phase);
                    state.setLastSeasonTick(now);
                    state.setProgress(0.0f);
                    state.setLeafState(LeafState.PARTIAL);
                    dirty = true;
                }
                if (state.lastSeasonApplied() == phase && state.leafState() == LeafState.PARTIAL) {
                    long startTick = state.lastSeasonTick() + offsetTicks;
                    if (now >= startTick) {
                        long durationTicks = getTransitionDurationTicks(phase, dayDuration);
                        float progress = durationTicks == 0 ? 1.0f : (float) (now - startTick) / (float) durationTicks;
                        progress = Mth.clamp(progress, 0.0f, 1.0f);
                        state.setProgress(progress);
                        if (progress >= 1.0f) {
                            state.setLeafState(phase == SeasonPhase.AUTUMN ? LeafState.HIBERNATING : LeafState.FULL);
                        }
                        dirty = true;
                    }
                }
            } else if (phase == SeasonPhase.WINTER) {
                if (!state.leafState().isDormant() || state.lastSeasonApplied() != phase) {
                    state.setLeafState(LeafState.HIBERNATING);
                    state.setProgress(1.0f);
                    state.setLastSeasonApplied(phase);
                    state.setLastSeasonTick(now);
                    dirty = true;
                }
            } else {
                if (state.leafState() != LeafState.FULL || state.lastSeasonApplied() != phase) {
                    state.setLeafState(LeafState.FULL);
                    state.setProgress(1.0f);
                    state.setLastSeasonApplied(phase);
                    state.setLastSeasonTick(now);
                    dirty = true;
                }
            }
            return dirty;
        }

        private float getVigorMultiplier(ServerLevel level, BlockPos pos) {
            if (VIGOR_PROVIDERS.isEmpty()) {
                return 1.0f;
            }
            float multiplier = 1.0f;
            for (SeasonalTreesVigorProvider provider : VIGOR_PROVIDERS) {
                multiplier *= provider.getVigorMultiplier(level, pos);
            }
            return multiplier;
        }

        private float seasonVigorFactor(SeasonPhase phase) {
            return switch (phase) {
                case SPRING -> 1.15f;
                case SUMMER -> 1.0f;
                case AUTUMN -> 0.7f;
                case WINTER -> 0.4f;
            };
        }

        private long getTransitionDurationTicks(SeasonPhase phase, long dayDuration) {
            double days = phase == SeasonPhase.AUTUMN
                    ? AtmoCommonConfig.SEASONAL_TREES_LEAF_DROP_DAYS.get()
                    : AtmoCommonConfig.SEASONAL_TREES_LEAF_REGROW_DAYS.get();
            return (long) (days * dayDuration);
        }

        private long computeOffsetTicks(SeasonPhase phase, long dayDuration, BlockPos rootPos) {
            double offsetDays = AtmoCommonConfig.SEASONAL_TREES_TRANSITION_OFFSET_DAYS.get();
            long maxOffset = (long) (offsetDays * dayDuration);
            if (maxOffset <= 0) {
                return 0L;
            }
            long seed = level.getSeed() ^ rootPos.asLong() ^ ((long) phase.ordinal() << 32);
            long offset = Math.abs(seed) % (maxOffset + 1);
            return offset;
        }

        private void trySpread(TreeRecord record, SeasonalTreesTreeAccessor accessor) {
            if (!accessor.isMature(level, record)) {
                return;
            }
            if (record.state().leafState() != LeafState.FULL) {
                return;
            }
            if (record.state().vigor() < AtmoCommonConfig.SEASONAL_TREES_VIGOR_MIN_FOR_SPREAD.get()) {
                return;
            }
            long dayDuration = Math.max(1L, net.Gabou.projectatmosphere.seasons.SeasonTimeHelper.dayDuration(level));
            double perDay = AtmoCommonConfig.SEASONAL_TREES_SPREAD_CHANCE_PER_DAY.get();
            double chance = perDay / (double) dayDuration;
            if (level.getRandom().nextDouble() > chance) {
                return;
            }
            ResourceLocation speciesId = accessor.getSpeciesId(level, record.key().rootPos());
            if (speciesId == null) {
                return;
            }
            SeedPayload payload = new SeedPayload(record.key().treeType(), speciesId, record.key().rootPos());
            boolean transported = WIND_TRANSPORT.isEnabled() && WIND_TRANSPORT.offerSeed(level, payload);
            if (!transported) {
                tryPlantSeed(level, payload);
            }
        }

        private void runScanning(int budget) {
            if (budget <= 0) {
                return;
            }
            List<SeasonalTreesTreeAccessor> accessors = ACCESSORS.getEnabledAccessors();
            if (accessors.isEmpty()) {
                return;
            }
            while (budget > 0 && !scanQueue.isEmpty()) {
                ChunkPos chunkPos = scanQueue.peek();
                long key = chunkPos.toLong();
                ChunkScanCursor cursor = scanCursors.computeIfAbsent(key, ignored -> new ChunkScanCursor());
                ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);
                while (budget > 0 && cursor.hasNext()) {
                    int index = cursor.next();
                    int localX = index & 15;
                    int localZ = (index >> 4) & 15;
                    for (SeasonalTreesTreeAccessor accessor : accessors) {
                        BlockPos root = accessor.findRootInColumn(level, chunk, localX, localZ);
                        if (root != null) {
                            ChunkPos rootChunk = new ChunkPos(root);
                            if (data.containsTree(rootChunk, root)) {
                                continue;
                            }
                            TreeRecord record = accessor.createRecord(level, root);
                            if (record != null) {
                                data.putTree(record);
                                processingQueue.add(record);
                            }
                        }
                    }
                    budget--;
                }
                if (!cursor.hasNext()) {
                    scanQueue.poll();
                    scanCursors.remove(key);
                }
            }
        }
    }

    private static final class ChunkScanCursor {
        private int index = 0;

        private boolean hasNext() {
            return index < 256;
        }

        private int next() {
            return index++;
        }
    }
}

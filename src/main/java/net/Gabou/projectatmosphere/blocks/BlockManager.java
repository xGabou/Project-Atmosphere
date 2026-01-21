package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.registry.ModBlocks;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.DelayedTaskScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockManager {
    // Add these near your other static fields
    private static final String WEATHER_DROP_TAG = "PA_WEATHER_DROP";

    /**
     * Global per-tick budget for weather-spawned ItemEntity (tokens refresh every tick).
     */
    private static final int GLOBAL_WEATHER_ITEM_BUDGET_PER_TICK = 64; // tune this (e.g., 32–128)
    private static int globalWeatherItemTokens = GLOBAL_WEATHER_ITEM_BUDGET_PER_TICK;

    /**
     * Called by EventHandler once per server tick (END phase) to reset the token bucket.
     */
    public static void resetGlobalWeatherItemBudget() {
        globalWeatherItemTokens = GLOBAL_WEATHER_ITEM_BUDGET_PER_TICK;
    }

    /**
     * Try consume N tokens from the global weather item budget.
     */
    private static boolean tryConsumeWeatherItemTokens(int n) {
        if (globalWeatherItemTokens >= n) {
            globalWeatherItemTokens -= n;
            return true;
        }
        return false;
    }


    /**
     * Spawns dust blocks around the player depending on wind strength.
     * Call this every 1000 ticks or conditionally.
     *
     * @param level     The server world
     * @param centerPos The central position (e.g., player's position)
     */
    public static void spawnDust(ServerLevel level, BlockPos centerPos) {
        RandomSource random = level.getRandom();
        WindVector windVector = ForecastOrchestrator.getWind(AtmosphereUtils.getBiomeKey(level, centerPos), level.getGameTime());
        float windStrength = windVector.baseSpeed();
        int maxSpawn = Math.min(10, (int) (windStrength * 8));

        for (int i = 0; i < maxSpawn; i++) {
            if (random.nextFloat() > windStrength) continue;

            double angle = windVector.angleRadians() + (random.nextDouble() - 0.5);
            double distance = 10 + random.nextDouble() * 10;

            int dx = (int) (Math.cos(angle) * distance);
            int dz = (int) (Math.sin(angle) * distance);
            int x = centerPos.getX() + dx;
            int z = centerPos.getZ() + dz;
            int y = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z)
            ).getY();

            BlockPos dustPos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(dustPos);
            BlockState groundState = level.getBlockState(dustPos.below());

            boolean validGround = groundState.is(Blocks.DIRT) ||
                    groundState.is(Blocks.SAND) ||
                    groundState.is(Blocks.GRAVEL);

            if (validGround &&
                    (state.isAir() || state.is(Blocks.SNOW)) &&
                    BlockPos.betweenClosedStream(dustPos.offset(-4, -1, -4), dustPos.offset(4, 1, 4))
                            .filter(pos -> level.getBlockState(pos).is(ModBlocks.DUST.get()))
                            .count() < 6) {
                level.setBlockAndUpdate(dustPos, ModBlocks.DUST.get().defaultBlockState());
            }
        }
    }

    /**
     * Spawns sand layers around the player depending on wind strength.
     * Converts to a full sand block once eight layers accumulate.
     *
     * @param level     The server world
     * @param centerPos The central position (e.g., player's position)
     */
    public static void spawnSand(ServerLevel level, BlockPos centerPos) {
        RandomSource random = level.getRandom();
        WindVector windVector = ForecastOrchestrator.getWind(AtmosphereUtils.getBiomeKey(level, centerPos), level.getGameTime());
        float windStrength = windVector.baseSpeed();
        int maxSpawn = Math.min(10, (int) (windStrength * 8));

        for (int i = 0; i < maxSpawn; i++) {
            if (random.nextFloat() > windStrength) continue;

            double angle = windVector.angleRadians() + (random.nextDouble() - 0.5);
            double distance = 10 + random.nextDouble() * 10;

            int dx = (int) (Math.cos(angle) * distance);
            int dz = (int) (Math.sin(angle) * distance);
            int x = centerPos.getX() + dx;
            int z = centerPos.getZ() + dz;
            int y = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z)
            ).getY();

            BlockPos sandPos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(sandPos);
            BlockState groundState = level.getBlockState(sandPos.below());

            boolean validGround = groundState.is(Blocks.DIRT) ||
                    groundState.is(Blocks.SAND) ||
                    groundState.is(Blocks.GRAVEL);

            if (!validGround) continue;

            if (state.isAir() || state.is(Blocks.SNOW)) {
                level.setBlockAndUpdate(sandPos, ModBlocks.SAND_LAYER.get().defaultBlockState());
            } else if (state.is(ModBlocks.SAND_LAYER.get())) {
                int layers = state.getValue(SandLayerBlock.LAYERS);
                if (layers < 8) {
                    level.setBlockAndUpdate(sandPos, state.setValue(SandLayerBlock.LAYERS, layers + 1));
                } else {
                    level.setBlockAndUpdate(sandPos, Blocks.SAND.defaultBlockState());
                }
            }
        }
    }


    /**
     * Clears all dust blocks around the player.
     *
     * @param level     The server world
     * @param centerPos The central position (e.g., player's position)
     */
    public static void clearDust(ServerLevel level, BlockPos centerPos) {
        RandomSource random = level.getRandom();
        int radius = 20;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos dustPos = centerPos.offset(dx, 0, dz);
                if (level.getBlockState(dustPos).is(Blocks.SAND)) {
                    level.setBlockAndUpdate(dustPos, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /**
     * Spawns lightweight "debris" items such as sticks or leaves to simulate wind-blown junk.
     *
     * @param level     The server world
     * @param centerPos Central position where debris should spawn around
     */


    public static void spawnCochonnerie(ServerLevel level, BlockPos centerPos) {



        int ENTITY_THRESHOLD = AtmoCommonConfig.MAX_STORM_DEBRIS_PER_CHUNK.get();


        ChunkPos chunkPos = new ChunkPos(centerPos);
        AABB chunkBox = new AABB(
                chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunkPos.getMaxBlockZ() + 1
        );


        long itemCount = level.getEntitiesOfClass(ItemEntity.class, chunkBox).size();

        if (itemCount >= ENTITY_THRESHOLD) {
            ProjectAtmosphere.LOGGER.debug("[Atmosphere] Skipping debris spawn — too many items in chunk at {}", chunkPos);
            return;
        }

        RandomSource random = level.getRandom();
        int debrisCount = 3 + random.nextInt(5);
        int allowedSpawn = Math.max(0, ENTITY_THRESHOLD - (int) itemCount);
        if (allowedSpawn <= 0) {
            return;
        }
        debrisCount = Math.min(debrisCount, allowedSpawn);

        int toSpawn = debrisCount;


        toSpawn = Math.min(toSpawn, globalWeatherItemTokens);
        if (toSpawn <= 0) {
            return;
        }
        for (int i = 0; i < debrisCount; i++) {
            if (!tryConsumeWeatherItemTokens(1)) break;
            double dx = centerPos.getX() + random.nextGaussian() * 5;
            double dy = centerPos.getY() + 1 + random.nextDouble();
            double dz = centerPos.getZ() + random.nextGaussian() * 5;

            ItemStack debrisItem = switch (random.nextInt(4)) {
                case 0 -> new ItemStack(Items.STICK);
                case 1 -> new ItemStack(Items.OAK_LEAVES);
                case 2 -> new ItemStack(Items.ROTTEN_FLESH);
                default -> new ItemStack(Items.DEAD_BUSH);
            };

            ItemEntity entity = new ItemEntity(level, dx, dy, dz, debrisItem);
            entity.getPersistentData().putBoolean(WEATHER_DROP_TAG, true);
            entity.setPickUpDelay(40);
            entity.setNoGravity(true);
            entity.setDeltaMovement(
                    random.nextGaussian() * 0.05,
                    0.1 + random.nextDouble() * 0.05,
                    random.nextGaussian() * 0.05
            );

            level.addFreshEntity(entity);

            DelayedTaskScheduler.schedule(400 + level.random.nextInt(100), () -> {
                if (!entity.isRemoved()) entity.discard();
            });
        }
    }


    /**
     * Simulates a tempest effect by sparsely spawning dust and debris in a region.
     *
     * @param level  The server world
     * @param center The center position (e.g., storm eye)
     * @param radius The radius (in blocks) to affect
     */
    public static void simulateTempesta(ServerLevel level, BlockPos center, int radius) {
        if(ProjectAtmosphere.DEBUG_MODE)
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Simulating tempest at {} with radius {}", center, radius);

        AsyncAtmosphereService.runStorm(() -> {
            RandomSource random = RandomSource.create();
            int step = radius >= 1000 ? 32 : (radius >= 500 ? 16 : 8);
            int dustChance = 10;
            int debrisChance = 20;

            List<Runnable> mainThreadTasks = new ArrayList<>();
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;


                    BlockPos posXZ = new BlockPos(x, 0, z);

                    AtomicInteger dustSpawned = new AtomicInteger(0);
                    AtomicInteger debrisSpawned = new AtomicInteger(0);

                    mainThreadTasks.add(() -> {
                        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, posXZ).getY();
                        mutablePos.set(x, y, z);

                        if (debrisSpawned.get() < 30 && random.nextInt(debrisChance) == 0) {
                            BlockManager.spawnCochonnerie(level, mutablePos.immutable());
                            debrisSpawned.incrementAndGet();
                            DelayedTaskScheduler.schedule(6000, () -> debrisSpawned.updateAndGet(v -> Math.max(0, v - 1)));
                        }

                        if (dustSpawned.get() < 8 && random.nextInt(dustChance) == 0) {
                            BlockManager.spawnDust(level, mutablePos.immutable());
                            dustSpawned.incrementAndGet();
                            DelayedTaskScheduler.schedule(2000, () -> dustSpawned.updateAndGet(v -> Math.max(0, v - 1)));
                        }

                    });


                }
            }


            int batchSize = 100;
            for (int i = 0; i < mainThreadTasks.size(); i += batchSize) {
                int start = i;
                int end = Math.min(i + batchSize, mainThreadTasks.size());
                List<Runnable> batch = mainThreadTasks.subList(start, end);

                level.getServer().execute(() -> {
                    for (Runnable task : batch) {
                        task.run();
                    }
                });
            }
            resetGlobalWeatherItemBudget();
        });
    }


}

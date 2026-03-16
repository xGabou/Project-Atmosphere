package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.client.sound.TornadoAudioClient;
import net.Gabou.projectatmosphere.modules.wind.WindMath;
import net.Gabou.projectatmosphere.registry.ModParticles;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.Gabou.projectatmosphere.client.render.SkyEffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Deque;
import java.util.Set;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;

@OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ClientTickHandler {

    private static int tickCounter = 0;
    private static final Set<TornadoInstance> prevTornadoes = new HashSet<>();
    private static final Set<Integer> culledRegionIds = new HashSet<>();
    private static final double CLOUD_RENDER_DISTANCE = AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get();
    private static final int CANOPY_CACHE_LIMIT = 16;
    private static final float CANOPY_CACHE_REFRESH_CHANCE = 0.35f;
    private static final Deque<BlockPos> CANOPY_CACHE = new ArrayDeque<>();

    private static int getRegionId(CloudRegion region) {
        return System.identityHashCode(region);
    }

    public static boolean isRegionCulled(CloudRegion region) {
        return culledRegionIds.contains(getRegionId(region));
    }


    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ClientSyncLock.isReady()) return;
        if (Minecraft.getInstance().isPaused()) return;

        SkyEffectState.beginFrame();
        tickCounter++;
        TornadoManager.tick(Minecraft.getInstance().level);
        Minecraft mc = Minecraft.getInstance();
        RainbowWeatherTracker.tick(mc);

        if (mc.level != null && mc.player != null) {
            CloudManager<?> manager = CloudManager.get(mc.level);
            List<CloudRegion> regions = manager.getCloudGenerator().getClouds();
            double playerX = mc.player.getX();
            double playerZ = mc.player.getZ();
            Set<Integer> nextCulled = new HashSet<>();
            for (CloudRegion region : regions) {
                double dx = region.getWorldX() - playerX;
                double dz = region.getWorldZ() - playerZ;
                double distSq = dx * dx + dz * dz;
                if (distSq > CLOUD_RENDER_DISTANCE * CLOUD_RENDER_DISTANCE) {
                    nextCulled.add(getRegionId(region));
                }
            }
            culledRegionIds.clear();
            culledRegionIds.addAll(nextCulled);
        }

        if (mc.level != null) {
            Set<TornadoInstance> current = new HashSet<>(TornadoManager.getActiveTornadoes());
            for (TornadoInstance tornado : current) {
                float baseVol = 0.35f + 0.45f * 0.75f;
                TornadoAudioClient.ensure(tornado, baseVol, 140f);
            }
            for (TornadoInstance t : prevTornadoes) {
                if (!current.contains(t)) {
                    TornadoAudioClient.stop(t);
                }
            }
            prevTornadoes.clear();
            prevTornadoes.addAll(current);
        }

        if (mc.level != null && mc.level.getGameTime() % 2 == 0) {
            for (TornadoInstance tornado : TornadoManager.getActiveTornadoes()) {
                TornadoRenderHandler.spawnDebrisParticles(tornado, (ClientLevel) mc.level);
            }
        }
        if (mc.level != null && mc.player != null) {
            BlockPos pos = mc.player.blockPosition();
            long gameTime = mc.level.getGameTime();
            BiomeInstanceKey key = new BiomeInstanceKey(
                    AtmosphereUtils.getBiomeLocation(pos, mc.level), pos);
            WindVector wind = ForecastOrchestrator.getWind(key, gameTime);
            float speed = WindMath.getSmoothGustedSpeed(wind, gameTime);

            if (speed >= 2.0f) {
                spawnWindLeafParticles(mc, wind, speed);
            }
        }

    }

    public static SimpleParticleType getSeasonalLeafParticle(ClientLevel level, BlockPos pos, RandomSource random) {
        SeasonStage season = getCurrentSeason(level, pos);

        List<SimpleParticleType> candidates = switch (season) {
            case AUTUMN -> List.of(
                    ModParticles.TRIANGLE_ORANGE.get(),
                    ModParticles.TRIANGLE_JAUNE.get(),
                    ModParticles.ROUND_ORANGE.get(),
                    ModParticles.ROUND_JAUNE.get(),
                    ModParticles.HEART_ORANGE.get(),
                    ModParticles.HEART_JAUNE.get()
            );
            case SPRING, SUMMER -> List.of(
                    ModParticles.TRIANGLE_VERT.get(),
                    ModParticles.ROUND_VERT.get(),
                    ModParticles.HEART_VERT.get()
            );
            default -> List.of();
        };

        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    public static SeasonStage getCurrentSeason(ClientLevel level, BlockPos pos) {
        return SeasonTimeHelper.stage(level);
    }
    public record WindSpawnData(BlockPos pos,
                                double x, double y, double z,
                                double vx, double vy, double vz) {}

    private static void spawnWindLeafParticles(Minecraft mc, WindVector wind, float speed) {
        ClientLevel level = (ClientLevel) mc.level;
        if (level == null || mc.player == null) {
            return;
        }

        RandomSource rand = level.random;
        BlockPos playerPos = mc.player.blockPosition();
        int attempts = Math.max(0, AtmoCommonConfig.WIND_LEAF_PARTICLE_ATTEMPTS_PER_TICK.get());
        double chancePerCandidate = AtmoCommonConfig.WIND_LEAF_PARTICLE_CHANCE_PER_CANDIDATE.get();

        for (int i = 0; i < attempts; i++) {
            BlockPos canopyPos = findCanopyPosNearPlayer(level, playerPos, rand);
            if (canopyPos == null) {
                continue;
            }
            if (rand.nextDouble() > chancePerCandidate) {
                continue;
            }

            WindSpawnData data = buildWindLeafSpawn(canopyPos, wind, speed, rand);
            SimpleParticleType particle = getSeasonalLeafParticle(level, canopyPos, rand);
            SimpleParticleType windStreaks = ModParticles.WIND_STREAKS.get();

            if (particle != null) {
                level.addParticle(particle, data.x(), data.y(), data.z(),
                        data.vx(), data.vy(), data.vz());
            }
            level.addParticle(windStreaks, data.x(), data.y(), data.z(),
                    data.vx(), data.vy(), data.vz());
        }
    }

    private static BlockPos findCanopyPosNearPlayer(ClientLevel level, BlockPos playerPos, RandomSource random) {
        int radius = Math.max(1, AtmoCommonConfig.WIND_LEAF_PARTICLE_RADIUS_BLOCKS.get());
        int radiusSq = radius * radius;

        if (tickCounter % 40 == 0 && !CANOPY_CACHE.isEmpty()) {
            pruneCanopyCache(level, playerPos, radiusSq);
        }

        boolean refresh = CANOPY_CACHE.size() < CANOPY_CACHE_LIMIT || random.nextFloat() < CANOPY_CACHE_REFRESH_CHANCE;
        if (!refresh) {
            BlockPos cached = sampleCanopyCache(level, playerPos, radiusSq, random);
            if (cached != null) {
                return cached;
            }
        }

        BlockPos sampled = scanRandomCanopyColumn(level, playerPos, random, radius);
        if (sampled != null) {
            cacheCanopy(sampled);
            return sampled;
        }

        return sampleCanopyCache(level, playerPos, radiusSq, random);
    }

    private static BlockPos scanRandomCanopyColumn(ClientLevel level, BlockPos playerPos, RandomSource random, int radius) {
        int scanUp = Math.max(0, AtmoCommonConfig.WIND_LEAF_PARTICLE_SCAN_UP.get());
        int scanDown = Math.max(0, AtmoCommonConfig.WIND_LEAF_PARTICLE_SCAN_DOWN.get());
        int yStart = Math.min(level.getMaxBuildHeight() - 1, playerPos.getY() + scanUp);
        int yEnd = Math.max(level.getMinBuildHeight(), playerPos.getY() - scanDown);
        if (yStart < yEnd) {
            return null;
        }

        double theta = random.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(random.nextDouble()) * radius;
        int x = playerPos.getX() + Mth.floor(Math.cos(theta) * dist);
        int z = playerPos.getZ() + Mth.floor(Math.sin(theta) * dist);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, yStart, z);
        if (!level.hasChunkAt(cursor)) {
            return null;
        }

        for (int y = yStart; y >= yEnd; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (isFoliage(level, cursor, state) && passesCanopyValidation(level, cursor)) {
                return cursor.immutable();
            }
        }

        return null;
    }

    private static boolean passesCanopyValidation(ClientLevel level, BlockPos pos) {
        int minNeighbors = Math.max(1, AtmoCommonConfig.WIND_LEAF_PARTICLE_MIN_FOLIAGE_NEIGHBORS.get());
        int foliageCount = countFoliageNeighbors(level, pos);
        if (foliageCount < minNeighbors) {
            return false;
        }

        if (!AtmoCommonConfig.WIND_LEAF_PARTICLE_REQUIRE_LOG_BELOW.get()) {
            return true;
        }

        int maxDepth = Math.max(1, AtmoCommonConfig.WIND_LEAF_PARTICLE_MAX_LOG_SEARCH_DEPTH.get());
        return hasLogBelow(level, pos, maxDepth);
    }

    private static int countFoliageNeighbors(ClientLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (isFoliage(level, cursor, state)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean hasLogBelow(ClientLevel level, BlockPos pos, int maxDepth) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= maxDepth; i++) {
            cursor.set(pos.getX(), pos.getY() - i, pos.getZ());
            if (!level.hasChunkAt(cursor)) {
                return false;
            }
            if (level.getBlockState(cursor).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFoliage(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.LEAVES)) {
            return true;
        }
        if (state.isAir()) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.PERSISTENT) || state.hasProperty(BlockStateProperties.DISTANCE)) {
            return true;
        }
        return !state.isSolidRender(level, pos) && state.getLightBlock(level, pos) <= 2;
    }

    private static BlockPos sampleCanopyCache(ClientLevel level, BlockPos playerPos, int radiusSq, RandomSource random) {
        if (CANOPY_CACHE.isEmpty()) {
            return null;
        }

        BlockPos selected = null;
        int seen = 0;
        Iterator<BlockPos> iterator = CANOPY_CACHE.iterator();
        while (iterator.hasNext()) {
            BlockPos candidate = iterator.next();
            if (candidate.distSqr(playerPos) > radiusSq) {
                iterator.remove();
                continue;
            }
            if (!level.hasChunkAt(candidate)) {
                iterator.remove();
                continue;
            }
            BlockState state = level.getBlockState(candidate);
            if (!isFoliage(level, candidate, state)) {
                iterator.remove();
                continue;
            }
            if (random.nextInt(++seen) == 0) {
                selected = candidate;
            }
        }
        return selected;
    }

    private static void pruneCanopyCache(ClientLevel level, BlockPos playerPos, int radiusSq) {
        Iterator<BlockPos> iterator = CANOPY_CACHE.iterator();
        while (iterator.hasNext()) {
            BlockPos candidate = iterator.next();
            if (candidate.distSqr(playerPos) > radiusSq) {
                iterator.remove();
                continue;
            }
            if (!level.hasChunkAt(candidate)) {
                iterator.remove();
                continue;
            }
            BlockState state = level.getBlockState(candidate);
            if (!isFoliage(level, candidate, state)) {
                iterator.remove();
            }
        }
    }

    private static void cacheCanopy(BlockPos pos) {
        if (CANOPY_CACHE.size() >= CANOPY_CACHE_LIMIT) {
            CANOPY_CACHE.removeFirst();
        }
        CANOPY_CACHE.addLast(pos);
    }

    private static WindSpawnData buildWindLeafSpawn(BlockPos canopyPos, WindVector wind, float speed, RandomSource random) {
        float angle = wind.angleRadians();
        double dx = -Math.sin(angle);
        double dz = Math.cos(angle);
        double drift = Math.min(0.08d, speed * 0.02d);
        double jitter = 0.02d;

        double spawnX = canopyPos.getX() + random.nextDouble();
        double spawnY = canopyPos.getY() + (random.nextDouble() * 0.6d - 0.1d);
        double spawnZ = canopyPos.getZ() + random.nextDouble();

        double vx = dx * drift + (random.nextDouble() - 0.5d) * jitter;
        double vy = -0.01d - random.nextDouble() * 0.02d;
        double vz = dz * drift + (random.nextDouble() - 0.5d) * jitter;

        return new WindSpawnData(canopyPos, spawnX, spawnY, spawnZ, vx, vy, vz);
    }

}

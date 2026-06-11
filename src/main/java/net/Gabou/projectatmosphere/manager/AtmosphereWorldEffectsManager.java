package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.api.AtmoApi;
import net.Gabou.projectatmosphere.api.AtmosphereWorldEffect;
import net.Gabou.projectatmosphere.api.WeatherSnapshot;
import net.Gabou.projectatmosphere.api.event.AtmosphereWeatherTickEvent;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AtmosphereWorldEffectsManager {
    private static final List<AtmosphereWorldEffect> EFFECTS = new CopyOnWriteArrayList<>();

    private AtmosphereWorldEffectsManager() {
    }

    public static void registerWorldEffect(AtmosphereWorldEffect effect) {
        if (effect == null) {
            return;
        }
        EFFECTS.add(effect);
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide) {
            return;
        }
        long gameTime = level.getGameTime();
        if (!AtmoCommonConfig.WORLD_EFFECTS_ENABLED.get()) {
            AtmosphereWorldEffectsDiagnostics.record(new AtmosphereWorldEffectsDiagnostics.FrameStats(
                    gameTime, false, level.players().size(), 0, 0, 0, 0, 0, 0, 0, 0, 0.0F
            ));
            return;
        }
        int samplesPerPlayer = AtmoCommonConfig.WORLD_EFFECT_SAMPLES_PER_PLAYER.get();
        int radius = AtmoCommonConfig.WORLD_EFFECT_SAMPLE_RADIUS.get();
        if (samplesPerPlayer <= 0 || radius <= 0) {
            AtmosphereWorldEffectsDiagnostics.record(new AtmosphereWorldEffectsDiagnostics.FrameStats(
                    gameTime, true, level.players().size(), 0, 0, 0, 0, 0, 0, 0, 0, 0.0F
            ));
            return;
        }

        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();
        int playerCount = 0;
        int sampleCount = 0;
        int rainySamples = 0;
        int skyBlockedSamples = 0;
        int firesRemoved = 0;
        int campfiresDoused = 0;
        int cauldronsFilled = 0;
        int eventHooks = 0;
        int customHooks = 0;
        float lastRainIntensity = 0.0F;

        boolean hasCustomEffects = !EFFECTS.isEmpty();
        for (ServerPlayer player : level.players()) {
            playerCount++;
            BlockPos origin = player.blockPosition();
            WeatherSnapshot playerSnapshot = AtmoApi.getInstance().getWeatherSnapshot(level, origin, gameTime);
            if (!hasCustomEffects && playerSnapshot.rainIntensity() <= 0f) {
                continue;
            }
            for (int i = 0; i < samplesPerPlayer; i++) {
                int dx = random.nextInt(radius * 2 + 1) - radius;
                int dz = random.nextInt(radius * 2 + 1) - radius;
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                samplePos.set(x, y, z);
                sampleCount++;

                WeatherSnapshot snapshot = AtmoApi.getInstance().getWeatherSnapshot(level, samplePos, gameTime);
                lastRainIntensity = snapshot.rainIntensity();
                if (snapshot.rainIntensity() > 0f) {
                    rainySamples++;
                }
                EffectCounters counters = applyPrecipitationEffects(level, random, samplePos, snapshot, targetPos);
                if (counters.skyBlocked()) {
                    skyBlockedSamples++;
                }
                firesRemoved += counters.firesRemoved();
                campfiresDoused += counters.campfiresDoused();
                cauldronsFilled += counters.cauldronsFilled();
                customHooks += fireModderHooks(level, random, samplePos, snapshot);
                eventHooks++;
            }
        }
        AtmosphereWorldEffectsDiagnostics.record(new AtmosphereWorldEffectsDiagnostics.FrameStats(
                gameTime,
                true,
                playerCount,
                sampleCount,
                rainySamples,
                skyBlockedSamples,
                firesRemoved,
                campfiresDoused,
                cauldronsFilled,
                eventHooks,
                customHooks,
                lastRainIntensity
        ));
    }

    public static void applyCloudCoverEffects(ServerLevel level, LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (mob.getMobType() != MobType.UNDEAD) {
            return;
        }
        if (!level.isDay()) {
            return;
        }
        BlockPos pos = entity.blockPosition();
        if (!level.canSeeSky(pos)) {
            return;
        }
        WeatherSnapshot snapshot = AtmoApi.getInstance().getWeatherSnapshot(level, pos, level.getGameTime());
        float cloudCover = snapshot.cloudCover();
        float burnThreshold = AtmoCommonConfig.CLOUD_BURN_PREVENT_THRESHOLD.get().floatValue();
        if (cloudCover < burnThreshold) {
            return;
        }
        int fireTicks = entity.getRemainingFireTicks();
        if (fireTicks <= 0) {
            return;
        }
        float dampThreshold = AtmoCommonConfig.CLOUD_FIRE_DAMP_THRESHOLD.get().floatValue();
        int dampTicks = AtmoCommonConfig.CLOUD_FIRE_DAMP_TICKS.get();
        if (cloudCover >= dampThreshold && dampTicks > 0) {
            entity.setRemainingFireTicks(Math.max(0, fireTicks - dampTicks));
        } else {
            entity.setRemainingFireTicks(0);
        }
    }

    private static EffectCounters applyPrecipitationEffects(ServerLevel level,
                                                  RandomSource random,
                                                  BlockPos.MutableBlockPos surfacePos,
                                                  WeatherSnapshot snapshot,
                                                  BlockPos.MutableBlockPos targetPos) {
        if (snapshot.rainIntensity() <= 0f) {
            return EffectCounters.NONE;
        }
        if (!level.canSeeSky(surfacePos)) {
            return EffectCounters.SKY_BLOCKED;
        }

        float intensity = Mth.clamp(snapshot.rainIntensity(), 0f, 1f);
        float fireChance = AtmoCommonConfig.FIRE_EXTINGUISH_BASE_CHANCE.get().floatValue() * intensity;
        float cauldronChance = LocalizedPrecipitationBlockUpdater.shouldUseVanillaCompatibility(level)
                ? 0.0F
                : AtmoCommonConfig.CAULDRON_FILL_BASE_CHANCE.get().floatValue() * intensity;
        if (fireChance <= 0f && cauldronChance <= 0f && EFFECTS.isEmpty()) {
            return EffectCounters.NONE;
        }

        EffectCounters surfaceCounters = handleBlockAt(level, random, surfacePos, snapshot, fireChance, cauldronChance);
        targetPos.set(surfacePos).move(0, 1, 0);
        EffectCounters targetCounters = handleBlockAt(level, random, targetPos, snapshot, fireChance, cauldronChance);
        return surfaceCounters.plus(targetCounters);
    }

    private static EffectCounters handleBlockAt(ServerLevel level,
                                      RandomSource random,
                                      BlockPos.MutableBlockPos pos,
                                      WeatherSnapshot snapshot,
                                      float fireChance,
                                      float cauldronChance) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof net.minecraft.world.level.block.BaseFireBlock || state.is(BlockTags.FIRE)) {
            if (random.nextFloat() < fireChance) {
                level.removeBlock(pos, false);
                return EffectCounters.fireRemoved();
            }
            return EffectCounters.NONE;
        }

        if (block instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            if (random.nextFloat() < fireChance) {
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, Boolean.FALSE), 11);
                level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.0);
                return EffectCounters.campfireDoused();
            }
            return EffectCounters.NONE;
        }

        if (block == Blocks.CAULDRON && random.nextFloat() < cauldronChance) {
            boolean snowing = snapshot.isSnowing();
            BlockState fillState = snowing ? Blocks.POWDER_SNOW_CAULDRON.defaultBlockState() : Blocks.WATER_CAULDRON.defaultBlockState();
            level.setBlock(pos, fillState, 3);
            return EffectCounters.cauldronFilled();
        }
        return EffectCounters.NONE;
    }

    private static int fireModderHooks(ServerLevel level, RandomSource random, BlockPos.MutableBlockPos pos, WeatherSnapshot snapshot) {
        BlockPos immutablePos = pos.immutable();
        MinecraftForge.EVENT_BUS.post(new AtmosphereWeatherTickEvent(level, immutablePos, snapshot));
        if (EFFECTS.isEmpty()) {
            return 0;
        }
        int customHooks = 0;
        for (AtmosphereWorldEffect effect : EFFECTS) {
            if (effect == null || effect.id() == null) {
                continue;
            }
            effect.tick(level, random, immutablePos, snapshot);
            customHooks++;
        }
        return customHooks;
    }

    private record EffectCounters(
            int firesRemoved,
            int campfiresDoused,
            int cauldronsFilled,
            boolean skyBlocked
    ) {
        private static final EffectCounters NONE = new EffectCounters(0, 0, 0, false);
        private static final EffectCounters SKY_BLOCKED = new EffectCounters(0, 0, 0, true);

        private static EffectCounters fireRemoved() {
            return new EffectCounters(1, 0, 0, false);
        }

        private static EffectCounters campfireDoused() {
            return new EffectCounters(0, 1, 0, false);
        }

        private static EffectCounters cauldronFilled() {
            return new EffectCounters(0, 0, 1, false);
        }

        private EffectCounters plus(EffectCounters other) {
            if (other == null) {
                return this;
            }
            return new EffectCounters(
                    firesRemoved + other.firesRemoved,
                    campfiresDoused + other.campfiresDoused,
                    cauldronsFilled + other.cauldronsFilled,
                    skyBlocked || other.skyBlocked
            );
        }
    }
}

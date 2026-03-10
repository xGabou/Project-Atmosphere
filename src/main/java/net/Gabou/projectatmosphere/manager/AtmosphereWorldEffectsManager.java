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
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;

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
        if (!AtmoCommonConfig.WORLD_EFFECTS_ENABLED.get()) {
            return;
        }
        int samplesPerPlayer = AtmoCommonConfig.WORLD_EFFECT_SAMPLES_PER_PLAYER.get();
        int radius = AtmoCommonConfig.WORLD_EFFECT_SAMPLE_RADIUS.get();
        if (samplesPerPlayer <= 0 || radius <= 0) {
            return;
        }

        long gameTime = level.getGameTime();
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();

        boolean hasCustomEffects = !EFFECTS.isEmpty();
        for (ServerPlayer player : level.players()) {
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

                WeatherSnapshot snapshot = AtmoApi.getInstance().getWeatherSnapshot(level, samplePos, gameTime);
                applyPrecipitationEffects(level, random, samplePos, snapshot, targetPos);
                fireModderHooks(level, random, samplePos, snapshot);
            }
        }
    }

    public static void applyCloudCoverEffects(ServerLevel level, LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (!mob.getType().is(EntityTypeTags.UNDEAD)) {
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

    private static void applyPrecipitationEffects(ServerLevel level,
                                                  RandomSource random,
                                                  BlockPos.MutableBlockPos surfacePos,
                                                  WeatherSnapshot snapshot,
                                                  BlockPos.MutableBlockPos targetPos) {
        if (snapshot.rainIntensity() <= 0f) {
            return;
        }
        if (!level.canSeeSky(surfacePos)) {
            return;
        }

        float intensity = Mth.clamp(snapshot.rainIntensity(), 0f, 1f);
        float fireChance = AtmoCommonConfig.FIRE_EXTINGUISH_BASE_CHANCE.get().floatValue() * intensity;
        float cauldronChance = AtmoCommonConfig.CAULDRON_FILL_BASE_CHANCE.get().floatValue() * intensity;
        if (fireChance <= 0f && cauldronChance <= 0f && EFFECTS.isEmpty()) {
            return;
        }

        handleBlockAt(level, random, surfacePos, snapshot, fireChance, cauldronChance);
        targetPos.set(surfacePos).move(0, 1, 0);
        handleBlockAt(level, random, targetPos, snapshot, fireChance, cauldronChance);
    }

    private static void handleBlockAt(ServerLevel level,
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
            }
            return;
        }

        if (block instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            if (random.nextFloat() < fireChance) {
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, Boolean.FALSE), 11);
                level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.0);
            }
            return;
        }

        if (block == Blocks.CAULDRON && random.nextFloat() < cauldronChance) {
            boolean snowing = snapshot.isSnowing();
            BlockState fillState = snowing ? Blocks.POWDER_SNOW_CAULDRON.defaultBlockState() : Blocks.WATER_CAULDRON.defaultBlockState();
            level.setBlock(pos, fillState, 3);
        }
    }

    private static void fireModderHooks(ServerLevel level, RandomSource random, BlockPos.MutableBlockPos pos, WeatherSnapshot snapshot) {
        BlockPos immutablePos = pos.immutable();
        NeoForge.EVENT_BUS.post(new AtmosphereWeatherTickEvent(level, immutablePos, snapshot));
        if (EFFECTS.isEmpty()) {
            return;
        }
        for (AtmosphereWorldEffect effect : EFFECTS) {
            if (effect == null || effect.id() == null) {
                continue;
            }
            effect.tick(level, random, immutablePos, snapshot);
        }
    }
}

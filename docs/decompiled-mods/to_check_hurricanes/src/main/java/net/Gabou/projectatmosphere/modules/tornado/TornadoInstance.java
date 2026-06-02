package net.Gabou.projectatmosphere.modules.tornado;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.ITornadoRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.Gabou.projectatmosphere.modules.weather.StormMotionModel;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class TornadoInstance {
    private static final int DEBRIS_RANGE_EXTENSION = 5;
    public static final double AMBIENT_WIND_INFLUENCE_EXTENSION = 15.0D;
    public static final double WIND_SPEED_SCALING_FACTOR = 0.05D;
    public static final double WIND_EFFECT_VERTICAL_MAX_OFFSET = 50.0D;
    public static final double WIND_EFFECT_VERTICAL_MIN_OFFSET = -5.0D;
    private static final int FLOW_FIELD_INTERVAL_TICKS = 2;
    private static final int DEMOLITION_INTERVAL_TICKS = 20;
    private static final float MIN_EFFECTIVE_WIND = 73.0F;
    private static final float MAX_EFFECTIVE_WIND = 260.0F;
    private static final float RANKINE_FACTOR = 4.5F;
    private static final float OUTER_FLOW_RADIUS_FACTOR = 1.24F;
    private static final float MID_SHELL_CENTER = 0.44F;
    private static final float MID_SHELL_WIDTH = 0.24F;
    private static final float CORE_ZONE_END = 0.22F;
    private static final float PLUME_FLOW_START = 0.70F;
    private static final float EJECTION_START_HEIGHT = 0.84F;
    private static final float CAPTURE_ENTRY_RADIUS = 0.84F;

    private final UUID id;
    public Vec3 position;
    public float radius;
    public WindVector wind;
    private final float maxRadius;
    private final float targetVisualHeight;
    private float visualBottomY;
    private float visualHeight;
    private float angularSpeed;
    private float normalizedIntensity;
    private float targetIntensity;
    private float headingRadians;
    private Vec3 motion = Vec3.ZERO;
    private boolean descriptorMissing;
    private int ageTicks;
    private int phaseTicks;
    private int formationTicks;
    private int activeTicks;
    private int dissipationTicks;
    private long spawnGameTime;
    private long lastAmbientWindTick = Long.MIN_VALUE;
    private long lastDemolitionTick = Long.MIN_VALUE;
    private float anchorX;
    private float anchorZ;
    private StormLifecyclePhase phase;
    private float recentDebrisScore;
    private final Map<Integer, CapturedEntityState> capturedEntities = new HashMap<>();

    @Nullable
    private CloudRegion cloudRegion;

    public TornadoInstance(Vec3 position, float radius, WindVector wind, @Nullable CloudRegion cloudRegion) {
        this(UUID.randomUUID(), position, radius, wind, 0.05F, (float) position.y, Math.max(96.0F, radius * 12.0F), cloudRegion);
    }

    public TornadoInstance(UUID id, Vec3 position, float radius, WindVector wind,
                           float visualBottomY, float visualHeight, @Nullable CloudRegion cloudRegion) {
        this(id, position, radius, wind, 0.05F, visualBottomY, visualHeight, cloudRegion);
    }

    public TornadoInstance(UUID id, Vec3 position, float radius, WindVector wind, float angularSpeed,
                           float visualBottomY, float visualHeight, @Nullable CloudRegion cloudRegion) {
        this.id = id;
        this.position = position;
        this.radius = radius;
        this.maxRadius = radius;
        this.wind = wind;
        this.angularSpeed = angularSpeed;
        this.visualBottomY = visualBottomY;
        this.visualHeight = visualHeight;
        this.targetVisualHeight = Math.max(visualHeight, 32.0F);
        this.cloudRegion = cloudRegion;
        this.anchorX = (float) position.x;
        this.anchorZ = (float) position.z;
        this.phase = StormLifecyclePhase.FORMING;
        this.targetIntensity = defaultTargetIntensity(radius, wind);
        this.normalizedIntensity = Math.max(0.18F, this.targetIntensity * 0.35F);
        this.headingRadians = wind.angleRadians();
        this.formationTicks = 120 + Mth.floor(this.targetIntensity * 120.0F);
        this.activeTicks = 1200 + Mth.floor(this.targetIntensity * 1800.0F);
        this.dissipationTicks = 160 + Mth.floor(this.targetIntensity * 180.0F);
        this.applyIntensityToVisuals();
    }

    public UUID getId() {
        return this.id;
    }

    @Nullable
    public CloudRegion getCloudRegion() {
        return this.cloudRegion;
    }

    public void setCloudRegion(@Nullable CloudRegion cloudRegion) {
        this.cloudRegion = cloudRegion;
    }

    public float getVisualBottomY() {
        return this.visualBottomY;
    }

    public float getVisualHeight() {
        return this.visualHeight;
    }

    public float getNormalizedIntensity() {
        return this.normalizedIntensity;
    }

    public StormLifecyclePhase getPhase() {
        return this.phase;
    }

    public float getRecentDebrisScore() {
        return this.recentDebrisScore;
    }

    public TornadoLevel getLevel() {
        return TornadoLevel.fromWindSpeed(this.getEffectiveWindSpeed());
    }

    public double getSuctionRadius() {
        return this.radius + this.getLevel().getBaseDamage() * 1.2D;
    }

    public double getDamageMultiplier() {
        return this.getLevel().getBaseDamage() * Math.max(0.3D, this.normalizedIntensity);
    }

    public float getLifetimeSeconds() {
        return this.ageTicks / 20.0F;
    }

    public float getTwist() {
        float elapsedTicks = this.ageTicks;
        return Mth.clamp(elapsedTicks * this.angularSpeed * 0.05F, 0.5F, 5.0F);
    }

    public boolean isDescriptorMissing() {
        return this.descriptorMissing;
    }

    public void markDissipating() {
        if (this.phase == StormLifecyclePhase.DISSIPATED || this.phase == StormLifecyclePhase.DISSIPATING) {
            return;
        }
        this.phase = StormLifecyclePhase.DISSIPATING;
        this.phaseTicks = 0;
    }

    public boolean isDead() {
        return this.phase.isTerminal();
    }

    public void tickServer(ServerLevel level, long gameTime) {
        this.ageTicks++;
        this.phaseTicks++;
        if (this.spawnGameTime == 0L) {
            this.spawnGameTime = gameTime;
        }

        WindVector sampledWind = ForecastOrchestrator.getWind(level, BlockPos.containing(this.position), gameTime);
        this.wind = sampledWind;
        this.recentDebrisScore = Math.max(0.0F, this.recentDebrisScore - 0.015F);
        this.pruneCapturedEntities(level);
        this.tickLifecycle();
        this.updateMovement(gameTime);
        this.pushStateToDescriptor();

        if (this.phase == StormLifecyclePhase.ACTIVE || this.phase == StormLifecyclePhase.DISSIPATING) {
            if (gameTime - this.lastAmbientWindTick >= FLOW_FIELD_INTERVAL_TICKS) {
                this.lastAmbientWindTick = gameTime;
                this.applyAmbientWind(level);
            }
            if (gameTime - this.lastDemolitionTick >= DEMOLITION_INTERVAL_TICKS && this.normalizedIntensity >= 0.35F) {
                this.lastDemolitionTick = gameTime;
                AsyncAtmosphereService.runStorm(() -> {
                    try {
                        this.demolishBlocks(level);
                        level.getServer().execute(() -> this.playDemolitionSound(level));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    public void tickClient() {
        this.ageTicks++;
    }

    public TornadoSnapshot snapshot() {
        return new TornadoSnapshot(
                this.id,
                this.position,
                this.radius,
                this.visualBottomY,
                this.visualHeight,
                this.wind.baseSpeed(),
                this.wind.angleRadians(),
                this.wind.gustSpeed(),
                this.normalizedIntensity,
                this.recentDebrisScore,
                this.phase
        );
    }

    public void applySnapshot(TornadoSnapshot snapshot, @Nullable CloudRegion region) {
        this.position = snapshot.position();
        this.radius = snapshot.radius();
        this.visualBottomY = snapshot.visualBottomY();
        this.visualHeight = snapshot.visualHeight();
        this.wind = new WindVector(snapshot.windSpeed(), snapshot.windAngle(), snapshot.windGust());
        this.normalizedIntensity = snapshot.normalizedIntensity();
        this.recentDebrisScore = snapshot.recentDebrisScore();
        this.phase = snapshot.phase();
        this.cloudRegion = region;
        this.anchorX = (float) this.position.x;
        this.anchorZ = (float) this.position.z;
    }

    public boolean synchronizeWithDescriptor() {
        TornadoDescriptor descriptor = this.findDescriptor();
        if (descriptor == null) {
            this.descriptorMissing = this.cloudRegion instanceof ITornadoRegion;
            return false;
        }

        this.descriptorMissing = false;
        this.visualBottomY = descriptor.getBottomY();
        this.visualHeight = descriptor.getHeight();
        this.radius = descriptor.getRadius();
        this.position = new Vec3(
                this.cloudRegion.getWorldX() + descriptor.getOffsetX(),
                descriptor.getBottomY(),
                this.cloudRegion.getWorldZ() + descriptor.getOffsetZ()
        );
        return true;
    }

    public void advanceByWind() {
        Vec3 updated = StormMotionModel.advanceTornado(
                this.id,
                this.position,
                this.motion,
                this.wind,
                Math.max(this.normalizedIntensity, 0.25F),
                this.ageTicks,
                this.anchorX,
                this.anchorZ
        );
        this.motion = updated.subtract(this.position);
        this.position = new Vec3(updated.x, this.visualBottomY, updated.z);
    }

    private void tickLifecycle() {
        switch (this.phase) {
            case FORMING -> {
                float rate = 1.0F / Math.max(1, this.formationTicks);
                this.normalizedIntensity = Math.min(this.targetIntensity, this.normalizedIntensity + rate);
                if (this.phaseTicks >= this.formationTicks || this.normalizedIntensity >= this.targetIntensity - 0.02F) {
                    this.phase = StormLifecyclePhase.ACTIVE;
                    this.phaseTicks = 0;
                }
            }
            case ACTIVE -> {
                this.normalizedIntensity = Mth.lerp(0.06F, this.normalizedIntensity, this.targetIntensity);
                if (this.phaseTicks >= this.activeTicks) {
                    this.phase = StormLifecyclePhase.DISSIPATING;
                    this.phaseTicks = 0;
                }
            }
            case DISSIPATING -> {
                float rate = 1.0F / Math.max(1, this.dissipationTicks);
                this.normalizedIntensity = Math.max(0.0F, this.normalizedIntensity - rate);
                if (this.phaseTicks >= this.dissipationTicks || this.normalizedIntensity <= 0.02F) {
                    this.phase = StormLifecyclePhase.DISSIPATED;
                    this.normalizedIntensity = 0.0F;
                }
            }
            case DISSIPATED -> this.normalizedIntensity = 0.0F;
        }
        this.applyIntensityToVisuals();
    }

    private void applyIntensityToVisuals() {
        float growth = Mth.clamp(this.normalizedIntensity, 0.0F, 1.0F);
        this.radius = Mth.lerp(growth, this.maxRadius * 0.32F, this.maxRadius);
        this.visualHeight = Mth.lerp(growth, this.targetVisualHeight * 0.35F, this.targetVisualHeight);
        this.angularSpeed = 0.08F + growth * 0.16F;
    }

    private void updateMovement(long gameTime) {
        Vec3 updated = StormMotionModel.advanceTornado(
                this.id,
                this.position,
                this.motion,
                this.wind,
                Math.max(this.normalizedIntensity, 0.08F),
                gameTime,
                this.anchorX,
                this.anchorZ
        );
        this.motion = updated.subtract(this.position);
        this.headingRadians = (float) Math.atan2(this.motion.z, this.motion.x);
        this.position = new Vec3(updated.x, this.visualBottomY, updated.z);
    }

    private void pushStateToDescriptor() {
        TornadoDescriptor descriptor = this.findDescriptor();
        if (descriptor == null) {
            this.descriptorMissing = this.cloudRegion instanceof ITornadoRegion;
            return;
        }
        this.descriptorMissing = false;
        descriptor.setBottomY(this.visualBottomY);
        descriptor.setHeight(this.visualHeight);
        descriptor.setRadius(this.radius);
        descriptor.setVelocityX((float) this.motion.x);
        descriptor.setVelocityZ((float) this.motion.z);
        if (this.cloudRegion != null) {
            descriptor.setOffsetX((float) (this.position.x - this.cloudRegion.getWorldX()));
            descriptor.setOffsetZ((float) (this.position.z - this.cloudRegion.getWorldZ()));
        }
    }

    private void applyAmbientWind(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        double influence = this.getWindfieldWidth() * 1.25D + AMBIENT_WIND_INFLUENCE_EXTENSION;
        double minY = this.position.y + WIND_EFFECT_VERTICAL_MIN_OFFSET;
        double maxY = this.position.y + 85.0D;
        AABB box = new AABB(
                this.position.x - influence, minY,
                this.position.z - influence, this.position.x + influence,
                maxY, this.position.z + influence
        );

        for (Entity entity : serverLevel.getEntities(null, box)) {
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            this.pullEntity(entity, entity instanceof Player ? 2.3F : 1.6F);
        }
    }

    private void demolishBlocks(ServerLevel level) {
        BlockPos center = BlockPos.containing(this.position);
        int intRadius = Mth.ceil(this.radius);
        double outerSq = (this.radius + 5.0F) * (this.radius + 5.0F);
        double innerSq = this.radius * this.radius;
        double band = Math.max(1.0, outerSq - innerSq);
        double invBand = 1.0 / band;
        BlockPos min = center.offset(-intRadius - DEBRIS_RANGE_EXTENSION, 0, -intRadius - DEBRIS_RANGE_EXTENSION);
        BlockPos max = center.offset(intRadius + DEBRIS_RANGE_EXTENSION, 8 + intRadius, intRadius + DEBRIS_RANGE_EXTENSION);

        RandomSource random = RandomSource.create(this.id.getLeastSignificantBits() ^ this.ageTicks);
        it.unimi.dsi.fastutil.longs.LongArrayList toDestroy = new it.unimi.dsi.fastutil.longs.LongArrayList(2048);
        it.unimi.dsi.fastutil.longs.LongArrayList toDestroyGlass = new it.unimi.dsi.fastutil.longs.LongArrayList(2048);
        it.unimi.dsi.fastutil.longs.LongArrayList toDestroyWeak = new it.unimi.dsi.fastutil.longs.LongArrayList(1024);
        it.unimi.dsi.fastutil.longs.LongArrayList toScourGrass = new it.unimi.dsi.fastutil.longs.LongArrayList(1024);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            try {
                LevelChunk chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
                if (chunk == null) {
                    continue;
                }

                BlockState state = chunk.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                float windEffect = this.getWindEffect(pos.getCenter());
                if (windEffect < 40.0F) {
                    continue;
                }
                double distSq = pos.distSqr(center);
                if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                    toDestroy.add(pos.asLong());
                } else if (AtmosphereUtils.isGlass(state)) {
                    if (distSq > outerSq) {
                        continue;
                    }

                    float pMax = 0.15F + this.normalizedIntensity * 0.35F;
                    double t = Mth.clamp((outerSq - distSq) * invBand, 0.0, 1.0);
                    float p = (float) (t * pMax);
                    if (random.nextFloat() < p) {
                        toDestroyGlass.add(pos.asLong());
                    }
                } else if (state.getFluidState().isEmpty()) {
                    if (state.is(Blocks.GRASS_BLOCK)) {
                        float scourChance = Mth.clamp((windEffect - 48.0F) / 110.0F, 0.0F, 1.0F) * (0.08F + this.normalizedIntensity * 0.18F);
                        if (random.nextFloat() < scourChance) {
                            toScourGrass.add(pos.asLong());
                        }
                    }
                    float destroySpeed = state.getDestroySpeed(level, pos);
                    if (destroySpeed >= 0.0F && destroySpeed <= 1.0F) {
                        float chance = Mth.clamp((windEffect - 85.0F) / 90.0F, 0.0F, 1.0F) * (0.03F + this.normalizedIntensity * 0.08F);
                        if (random.nextFloat() < chance) {
                            toDestroyWeak.add(pos.asLong());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        int perTick = 256;
        this.destroyCursor = 0;
        this.weakDestroyCursor = 0;
        if (!toDestroy.isEmpty()) {
            AsyncAtmosphereService.runOnMainThread(() -> this.processLeafLogDestruction(level, toDestroy, perTick));
        }
        if (!toDestroyWeak.isEmpty()) {
            AsyncAtmosphereService.runOnMainThread(() -> this.processWeakDestruction(level, toDestroyWeak, perTick));
        }
        if (!toScourGrass.isEmpty()) {
            AsyncAtmosphereService.runOnMainThread(() -> this.processGrassScouring(level, toScourGrass, perTick));
        }
        if (!toDestroyGlass.isEmpty()) {
            GlassDamageManager.damageGlass(level, toDestroyGlass);
        }
        float debrisGain = Math.min(1.0F,
                toDestroy.size() * 0.012F
                        + toDestroyWeak.size() * 0.020F
                        + toScourGrass.size() * 0.010F
                        + toDestroyGlass.size() * 0.028F);
        this.recentDebrisScore = Mth.clamp(this.recentDebrisScore + debrisGain, 0.0F, 1.0F);
    }

    private int destroyCursor = 0;
    private int weakDestroyCursor = 0;
    private int grassScourCursor = 0;

    private void processLeafLogDestruction(ServerLevel level,
                                           it.unimi.dsi.fastutil.longs.LongArrayList list,
                                           int perTick) {
        if (this.destroyCursor >= list.size()) {
            this.destroyCursor = 0;
            return;
        }

        int end = Math.min(this.destroyCursor + perTick, list.size());
        for (int i = this.destroyCursor; i < end; i++) {
            BlockPos pos = BlockPos.of(list.getLong(i));
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS))) {
                continue;
            }
            level.destroyBlock(pos, false);
        }

        this.destroyCursor = end;
        if (this.destroyCursor < list.size()) {
            level.getServer().execute(() -> this.processLeafLogDestruction(level, list, perTick));
        } else {
            this.destroyCursor = 0;
        }
    }

    private void processWeakDestruction(ServerLevel level,
                                        it.unimi.dsi.fastutil.longs.LongArrayList list,
                                        int perTick) {
        if (this.weakDestroyCursor >= list.size()) {
            this.weakDestroyCursor = 0;
            return;
        }

        int end = Math.min(this.weakDestroyCursor + perTick, list.size());
        for (int i = this.weakDestroyCursor; i < end; i++) {
            BlockPos pos = BlockPos.of(list.getLong(i));
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            float destroySpeed = state.getDestroySpeed(level, pos);
            if (destroySpeed < 0.0F || destroySpeed > 1.0F) {
                continue;
            }
            level.destroyBlock(pos, false);
        }

        this.weakDestroyCursor = end;
        if (this.weakDestroyCursor < list.size()) {
            level.getServer().execute(() -> this.processWeakDestruction(level, list, perTick));
        } else {
            this.weakDestroyCursor = 0;
        }
    }

    private void processGrassScouring(ServerLevel level,
                                      it.unimi.dsi.fastutil.longs.LongArrayList list,
                                      int perTick) {
        if (this.grassScourCursor >= list.size()) {
            this.grassScourCursor = 0;
            return;
        }

        int end = Math.min(this.grassScourCursor + perTick, list.size());
        for (int i = this.grassScourCursor; i < end; i++) {
            BlockPos pos = BlockPos.of(list.getLong(i));
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.GRASS_BLOCK)) {
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            }
        }

        this.grassScourCursor = end;
        if (this.grassScourCursor < list.size()) {
            level.getServer().execute(() -> this.processGrassScouring(level, list, perTick));
        } else {
            this.grassScourCursor = 0;
        }
    }

    private void playDemolitionSound(Level level) {
        BlockPos center = BlockPos.containing(this.position);
        level.playLocalSound(
                center.getX(), center.getY(), center.getZ(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.WEATHER,
                1.0F + this.normalizedIntensity,
                0.6F + level.getRandom().nextFloat() * 0.3F,
                false
        );
    }

    @Nullable
    private TornadoDescriptor findDescriptor() {
        if (!(this.cloudRegion instanceof ITornadoRegion tornadoRegion)) {
            return null;
        }
        return tornadoRegion.findTornado(this.id);
    }

    private static float defaultTargetIntensity(float radius, WindVector wind) {
        float radiusFactor = Mth.clamp((radius - 5.0F) / 20.0F, 0.0F, 1.0F);
        float windFactor = Mth.clamp((wind.baseSpeed() - 12.0F) / 28.0F, 0.0F, 1.0F);
        return Mth.clamp(0.35F + radiusFactor * 0.4F + windFactor * 0.25F, 0.25F, 1.0F);
    }

    private float getWindfieldWidth() {
        return Math.max(this.radius * 1.8F, 28.0F);
    }

    private float getRankine(double dist, float windfieldWidth) {
        float rankineWidth = windfieldWidth / RANKINE_FACTOR;
        float perc = 0.0F;
        if (dist <= rankineWidth * 0.5F) {
            perc = (float) dist / (rankineWidth * 0.5F);
        } else if (dist <= windfieldWidth * 2.0F) {
            double denom = ((windfieldWidth * 2.0F - rankineWidth) / 2.0F);
            perc = Mth.clamp((float) Math.pow(1.0F - (dist - rankineWidth * 0.5F) / denom, 1.5D), 0.0F, 1.0F);
        }
        return Float.isNaN(perc) ? 0.0F : perc;
    }

    private float getWindEffect(Vec3 pos) {
        float windfieldWidth = this.getWindfieldWidth();
        Vec3 flatCenter = new Vec3(this.position.x, 0.0D, this.position.z);
        Vec3 flatPos = new Vec3(pos.x, 0.0D, pos.z);
        double dist = flatCenter.distanceTo(flatPos);
        if (dist > windfieldWidth * 2.0F) {
            return 0.0F;
        }

        float perc = this.getRankine(dist, windfieldWidth);
        float affectPerc = (float) Math.sqrt(Math.max(0.0D, 1.0D - dist / (windfieldWidth * 2.0F)));
        Vec3 relativePos = pos.subtract(this.position);
        Vec3 rotational = new Vec3(relativePos.z, 0.0D, -relativePos.x);
        if (rotational.lengthSqr() > 1.0E-4) {
            rotational = rotational.normalize();
        } else {
            rotational = Vec3.ZERO;
        }

        float noise = 1.0F + StormMotionModel.noiseSigned(this.id, this.ageTicks + Mth.floor(dist * 3.0D), 0.035F) * 0.08F;
        double realWind = this.getEffectiveWindSpeed() * noise;
        Vec3 localMotion = rotational.scale(realWind * perc * 0.08D);
        localMotion = localMotion.add(this.motion.scale(10.0D * affectPerc));
        return (float) localMotion.length();
    }

    private void pullEntity(Entity entity, float multiplier) {
        float windfieldWidth = this.getWindfieldWidth();
        CapturedEntityState captured = this.capturedEntities.get(entity.getId());
        int worldHeight = entity.level().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                entity.blockPosition().getX(),
                entity.blockPosition().getZ()
        );
        if (worldHeight > entity.blockPosition().getY()) {
            return;
        }

        Vec3 targetPos = new Vec3(this.position.x, entity.position().y, this.position.z);
        double dist = entity.position().distanceTo(targetPos);
        double maxInfluenceDistance = windfieldWidth * (captured != null ? 1.28D : OUTER_FLOW_RADIUS_FACTOR);
        if (dist > maxInfluenceDistance) {
            return;
        }

        Vec3 relativePos = entity.position().subtract(this.position);
        double heightDifference = entity.position().y - this.position.y;
        if (Math.abs(heightDifference) > 150.0D) {
            return;
        }

        Vec3 inward = new Vec3(-relativePos.x, 0.0D, -relativePos.z);
        if (inward.lengthSqr() > 1.0E-4) {
            inward = inward.normalize();
        } else {
            inward = Vec3.ZERO;
        }

        Vec3 rotational = new Vec3(relativePos.z, 0.0D, -relativePos.x);
        if (rotational.lengthSqr() > 1.0E-4) {
            rotational = rotational.normalize();
        } else {
            rotational = Vec3.ZERO;
        }

        double windEffect = this.getWindEffect(entity.position());
        if (windEffect < (captured != null ? 16.0D : 24.0D)) {
            return;
        }

        float radialNorm = Mth.clamp((float) (dist / Math.max(windfieldWidth, 0.001F)), 0.0F, 1.35F);
        float heightNorm = Mth.clamp((float) ((entity.getY() - this.visualBottomY) / Math.max(this.visualHeight, 1.0F)), 0.0F, 1.15F);
        float outerReach = 1.0F - smoothStep(0.68F, 1.10F, radialNorm);
        float shellZone = 1.0F - Mth.clamp(Math.abs(radialNorm - MID_SHELL_CENTER) / MID_SHELL_WIDTH, 0.0F, 1.0F);
        float coreZone = 1.0F - smoothStep(0.05F, CORE_ZONE_END, radialNorm);
        float plumeZone = smoothStep(PLUME_FLOW_START, 0.98F, heightNorm);
        boolean shouldCapture = dist <= windfieldWidth * CAPTURE_ENTRY_RADIUS
                || (shellZone > 0.42F && windEffect >= 58.0D)
                || (coreZone > 0.45F && windEffect >= 48.0D);
        if (captured == null && shouldCapture) {
            captured = this.createCaptureState(entity, windfieldWidth, dist);
            this.capturedEntities.put(entity.getId(), captured);
        }

        double effectStrength = Mth.clamp(
                (float) ((windEffect - 25.0D) / Math.max(this.getEffectiveWindSpeed() * 1.15F, 130.0F)),
                0.0F,
                1.0F
        ) * multiplier * (1.02D + this.normalizedIntensity * 1.32D);

        Vec3 add;
        boolean releaseCapture = false;
        if (captured != null) {
            captured.lastSeenAge = this.ageTicks;
            captured.captureTicks++;
            float captureProgress = smoothStep(3.0F, 16.0F, captured.captureTicks);
            float transportProgress = smoothStep(12.0F, 46.0F, captured.captureTicks);
            if (!captured.expelling && (heightNorm >= EJECTION_START_HEIGHT || captured.captureTicks > 120)) {
                captured.expelling = true;
            }
            float desiredBand = Mth.clamp(
                    Mth.lerp(transportProgress, 0.20F + shellZone * 0.04F, Mth.lerp(plumeZone, 0.42F, 0.58F + plumeZone * 0.12F)),
                    0.16F,
                    0.72F
            );
            captured.radialBand = Mth.lerp(0.22F, captured.radialBand, desiredBand);
            captured.orbitAngle += (0.22F + this.normalizedIntensity * 0.20F + shellZone * 0.24F + transportProgress * 0.18F)
                    * (1.0F - plumeZone * 0.22F)
                    * captured.orbitBias;

            double desiredRadius = windfieldWidth * captured.radialBand;
            double desiredHeight = this.visualBottomY + this.visualHeight * Mth.clamp(
                    heightNorm + 0.02F + captured.liftBias * 0.04F + plumeZone * 0.05F + captureProgress * 0.08F + transportProgress * 0.14F,
                    0.04F,
                    1.04F
            );
            Vec3 orbitTarget = new Vec3(
                    Math.cos(captured.orbitAngle) * desiredRadius,
                    desiredHeight,
                    Math.sin(captured.orbitAngle) * desiredRadius
            ).add(this.position.x, 0.0D, this.position.z);
            Vec3 towardOrbit = orbitTarget.subtract(entity.position());
            Vec3 orbitCorrection = towardOrbit.lengthSqr() > 1.0E-4 ? towardOrbit.normalize() : Vec3.ZERO;
            double inwardStrength = captured.expelling
                    ? -0.16D
                    : Mth.lerp(captureProgress, 2.15D + outerReach * 0.95D + shellZone * 0.44D, 0.38D + shellZone * 0.32D - coreZone * 0.05D);
            double liftStrength = captured.expelling
                    ? effectStrength * (0.34D + plumeZone * 0.22D)
                    : (0.01D + captured.liftBias * 0.08D + plumeZone * 0.08D + captureProgress * 0.10D + transportProgress * 0.42D) * effectStrength;
            double tangentialStrength = captured.expelling
                    ? effectStrength * (0.72D + plumeZone * 0.22D)
                    : effectStrength * (0.42D + shellZone * 1.65D + captured.orbitBias * 0.48D + transportProgress * 1.28D);
            double ejectFactor = captured.expelling
                    ? 1.35D + plumeZone * 0.78D
                    : 0.0D;
            Vec3 outward = inward.scale(-1.0D);

            add = orbitCorrection.scale(effectStrength * 1.15D)
                    .add(rotational.scale(tangentialStrength))
                    .add(inward.scale(effectStrength * inwardStrength))
                    .add(outward.scale(effectStrength * ejectFactor))
                    .add(0.0D, liftStrength, 0.0D)
                    .scale(captured.expelling ? 0.115D : 0.125D);
            releaseCapture = captured.expelling && (heightNorm > 1.04F || dist > windfieldWidth * 1.26F);
        } else {
            double suctionStrength = 1.95D + outerReach * 2.85D + shellZone * 0.28D - coreZone * 0.10D;
            double tangentialStrength = 0.08D + shellZone * 0.34D + plumeZone * 0.06D;
            double liftStrength = 0.0D + shellZone * 0.02D + plumeZone * 0.04D;

            add = inward.scale(effectStrength * suctionStrength)
                    .add(rotational.scale(effectStrength * tangentialStrength))
                    .add(0.0D, effectStrength * liftStrength, 0.0D)
                    .scale(0.082D + this.normalizedIntensity * 0.028D);
        }
        if (captured != null) {
            Vec3 current = entity.getDeltaMovement();
            Vec3 damped = captured.expelling
                    ? new Vec3(current.x * 0.72D, Math.max(current.y * 0.78D, -0.08D), current.z * 0.72D)
                    : new Vec3(current.x * 0.38D, Math.max(current.y * 0.58D, -0.08D), current.z * 0.38D);
            entity.setDeltaMovement(damped.add(add));
        } else {
            entity.addDeltaMovement(add);
        }
        if (releaseCapture) {
            this.capturedEntities.remove(entity.getId());
        }
        if (entity instanceof Player) {
            entity.hurtMarked = true;
        }
        if (entity.getDeltaMovement().y > -0.25D) {
            entity.fallDistance = 0.0F;
        }
    }

    private CapturedEntityState createCaptureState(Entity entity, float windfieldWidth, double dist) {
        double angle = Math.atan2(entity.getZ() - this.position.z, entity.getX() - this.position.x);
        float band = Mth.clamp((float) (dist / Math.max(windfieldWidth, 0.001F)), 0.22F, 0.48F);
        float orbitBias = 0.85F + (float) StormMotionModel.noise01(this.id, this.ageTicks + entity.getId(), 0.11F) * 0.65F;
        float liftBias = 0.45F + (float) StormMotionModel.noise01(this.id, this.ageTicks + entity.getId() * 3L, 0.07F) * 0.55F;
        return new CapturedEntityState(angle, band, orbitBias, liftBias, this.ageTicks, 0);
    }

    private void pruneCapturedEntities(ServerLevel level) {
        Iterator<Map.Entry<Integer, CapturedEntityState>> iterator = this.capturedEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, CapturedEntityState> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive()) {
                iterator.remove();
                continue;
            }
            CapturedEntityState state = entry.getValue();
            if ((this.ageTicks - state.lastSeenAge) > 22) {
                iterator.remove();
            }
        }
    }

    private float getEffectiveWindSpeed() {
        return Mth.lerp(this.normalizedIntensity, MIN_EFFECTIVE_WIND, MAX_EFFECTIVE_WIND);
    }

    private static final class CapturedEntityState {
        private double orbitAngle;
        private float radialBand;
        private final float orbitBias;
        private final float liftBias;
        private int lastSeenAge;
        private int captureTicks;
        private boolean expelling;

        private CapturedEntityState(double orbitAngle, float radialBand, float orbitBias, float liftBias, int lastSeenAge, int captureTicks) {
            this.orbitAngle = orbitAngle;
            this.radialBand = radialBand;
            this.orbitBias = orbitBias;
            this.liftBias = liftBias;
            this.lastSeenAge = lastSeenAge;
            this.captureTicks = captureTicks;
            this.expelling = false;
        }
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class TornadoInstance {
    private static final int DEBRIS_RANGE_EXTENSION = 5;
    public static final double AMBIENT_WIND_INFLUENCE_EXTENSION = 15.0D;
    public static final double WIND_SPEED_SCALING_FACTOR = 0.05D;
    public static final double WIND_EFFECT_VERTICAL_MAX_OFFSET = 50.0D;
    public static final double WIND_EFFECT_VERTICAL_MIN_OFFSET = -5.0D;
    private static final int AMBIENT_WIND_INTERVAL_TICKS = 40;
    private static final int DEMOLITION_INTERVAL_TICKS = 20;
    private static final float MIN_EFFECTIVE_WIND = 73.0F;
    private static final float MAX_EFFECTIVE_WIND = 260.0F;
    private static final float RANKINE_FACTOR = 4.5F;

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
        this.tickLifecycle();
        this.updateMovement(gameTime);
        this.pushStateToDescriptor();

        if (this.phase == StormLifecyclePhase.ACTIVE || this.phase == StormLifecyclePhase.DISSIPATING) {
            if (gameTime - this.lastAmbientWindTick >= AMBIENT_WIND_INTERVAL_TICKS) {
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
        if (!toDestroyGlass.isEmpty()) {
            GlassDamageManager.damageGlass(level, toDestroyGlass);
        }
    }

    private int destroyCursor = 0;
    private int weakDestroyCursor = 0;

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
        if (dist > windfieldWidth) {
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
        if (windEffect < 30.0D) {
            return;
        }

        double effectStrength = Mth.clamp(
                (float) ((windEffect - 25.0D) / Math.max(this.getEffectiveWindSpeed() * 1.15F, 130.0F)),
                0.0F,
                1.0F
        ) * multiplier * (0.85D + this.normalizedIntensity * 1.1D);

        double pullFactor = 4.0D;
        pullFactor -= Math.max(heightDifference, 0.0D) / 70.0D * 3.0D;
        pullFactor /= Math.max(this.radius / 60.0F, 1.0F);
        if (dist <= windfieldWidth / (RANKINE_FACTOR * 2.0F)) {
            pullFactor = -1.2D;
        }

        Vec3 add = inward.scale(effectStrength * pullFactor)
                .add(rotational.scale(effectStrength * 1.25D))
                .add(0.0D, effectStrength * 0.95D, 0.0D)
                .scale(0.075D);
        entity.addDeltaMovement(add);
        if (entity instanceof Player) {
            entity.hurtMarked = true;
        }
        if (entity.getDeltaMovement().y > -0.25D) {
            entity.fallDistance = 0.0F;
        }
    }

    private float getEffectiveWindSpeed() {
        return Mth.lerp(this.normalizedIntensity, MIN_EFFECTIVE_WIND, MAX_EFFECTIVE_WIND);
    }
}

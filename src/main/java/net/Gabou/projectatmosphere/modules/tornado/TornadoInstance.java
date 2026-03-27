package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.ITornadoRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.Gabou.projectatmosphere.modules.weather.StormMotionModel;
import net.Gabou.projectatmosphere.modules.weather.StormSeverityScale;
import net.Gabou.projectatmosphere.modules.weather.StormShieldManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
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
    private static final int FLOW_FIELD_INTERVAL_TICKS = 1;
    private static final int DEMOLITION_INTERVAL_TICKS = 10;
    private static final float MIN_EFFECTIVE_WIND = 73.0F;
    private static final float MAX_EFFECTIVE_WIND = 260.0F;
    private static final float RANKINE_FACTOR = 4.5F;
    private static final float OUTER_FLOW_RADIUS_FACTOR = 1.24F;
    private static final float MID_SHELL_CENTER = 0.44F;
    private static final float MID_SHELL_WIDTH = 0.24F;
    private static final float CORE_ZONE_END = 0.22F;
    private static final float PLUME_FLOW_START = 0.70F;
    private static final float EJECTION_START_HEIGHT = 0.84F;
    private static final float CAPTURE_ENTRY_RADIUS = 0.92F;
    private static final float CAPTURED_ENTITY_ORBIT_SPEED_SCALE = 0.58F;
    private static final float CAPTURED_ENTITY_TANGENTIAL_SCALE = 0.62F;
    private static final float UNCAPTURED_ENTITY_TANGENTIAL_SCALE = 0.70F;
    private static final double ENTITY_LIFT_VECTOR_SCALE = 4.0D;
    private static final float WATER_PENALTY_THRESHOLD = 0.20F;
    private static final float WATER_FORCED_DISSIPATION_THRESHOLD = 0.65F;
    private static final int DEMOLITION_DEBUG_LOG_INTERVAL_TICKS = 100;

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
    private int stormLevel;
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
    private Vec3 clientPreviousRenderPosition;
    private Vec3 clientRenderPosition;
    private float clientPreviousRenderBottomY;
    private float clientRenderBottomY;
    private float clientPreviousRenderHeight;
    private float clientRenderHeight;
    private float clientPreviousRenderRadius;
    private float clientRenderRadius;
    private final Map<Integer, CapturedEntityState> capturedEntities = new HashMap<>();

    @Nullable
    private CloudRegion cloudRegion;

    public TornadoInstance(Vec3 position, float radius, WindVector wind, @Nullable CloudRegion cloudRegion) {
        this(UUID.randomUUID(), position, radius, wind, 0.05F, (float) position.y, Math.max(96.0F, radius * 12.0F), cloudRegion, StormSeverityScale.fromNormalized(Mth.clamp((radius - 5.0F) / 20.0F, 0.25F, 1.0F)));
    }

    public TornadoInstance(UUID id, Vec3 position, float radius, WindVector wind,
                           float visualBottomY, float visualHeight, @Nullable CloudRegion cloudRegion) {
        this(id, position, radius, wind, 0.05F, visualBottomY, visualHeight, cloudRegion, StormSeverityScale.fromNormalized(Mth.clamp((radius - 5.0F) / 20.0F, 0.25F, 1.0F)));
    }

    public TornadoInstance(UUID id, Vec3 position, float radius, WindVector wind, float angularSpeed,
                           float visualBottomY, float visualHeight, @Nullable CloudRegion cloudRegion) {
        this(id, position, radius, wind, angularSpeed, visualBottomY, visualHeight, cloudRegion, StormSeverityScale.fromNormalized(Mth.clamp((radius - 5.0F) / 20.0F, 0.25F, 1.0F)));
    }

    public TornadoInstance(UUID id, Vec3 position, float radius, WindVector wind, float angularSpeed,
                           float visualBottomY, float visualHeight, @Nullable CloudRegion cloudRegion, int stormLevel) {
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
        this.stormLevel = StormSeverityScale.clamp(stormLevel);
        this.anchorX = (float) position.x;
        this.anchorZ = (float) position.z;
        this.phase = StormLifecyclePhase.FORMING;
        this.targetIntensity = defaultTargetIntensity(radius, wind, this.stormLevel);
        this.normalizedIntensity = Math.max(0.18F, this.targetIntensity * 0.35F);
        this.headingRadians = wind.angleRadians();
        this.formationTicks = 120 + Mth.floor(this.targetIntensity * 120.0F) - this.stormLevel * 6;
        this.activeTicks = 1000 + Mth.floor(this.targetIntensity * 1400.0F) + this.stormLevel * 120;
        this.dissipationTicks = 140 + Mth.floor(this.targetIntensity * 160.0F) + this.stormLevel * 10;
        this.applyIntensityToVisuals();
        this.clientPreviousRenderPosition = position;
        this.clientRenderPosition = position;
        this.clientPreviousRenderBottomY = this.visualBottomY;
        this.clientRenderBottomY = this.visualBottomY;
        this.clientPreviousRenderHeight = this.visualHeight;
        this.clientRenderHeight = this.visualHeight;
        this.clientPreviousRenderRadius = this.radius;
        this.clientRenderRadius = this.radius;
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

    public int getStormLevel() {
        return this.stormLevel;
    }

    public float getRecentDebrisScore() {
        return this.recentDebrisScore;
    }

    public Vec3 getRenderPosition(float partialTick) {
        return this.clientPreviousRenderPosition.lerp(this.clientRenderPosition, Mth.clamp(partialTick, 0.0F, 1.0F));
    }

    public float getRenderBottomY(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.clientPreviousRenderBottomY, this.clientRenderBottomY);
    }

    public float getRenderHeight(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.clientPreviousRenderHeight, this.clientRenderHeight);
    }

    public float getRenderRadius(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.clientPreviousRenderRadius, this.clientRenderRadius);
    }

    public TornadoLevel getLevel() {
        return TornadoLevel.fromWindSpeed(this.getEffectiveWindSpeed());
    }

    public double getSuctionRadius() {
        return this.radius + this.getLevel().getBaseDamage() * 1.2D;
    }

    public double getDamageMultiplier() {
        return this.getLevel().getBaseDamage()
                * Math.max(0.3D, this.normalizedIntensity)
                * (0.75D + StormSeverityScale.toNormalized(this.stormLevel) * 0.65D);
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
        this.refreshStormLevel(level, gameTime);
        float waterExposure = this.sampleWaterExposure(level);
        this.applyWaterPenalty(waterExposure);
        this.recentDebrisScore = Math.max(0.0F, this.recentDebrisScore - 0.015F);
        this.pruneCapturedEntities(level);
        this.tickLifecycle();
        this.updateMovement(level, gameTime);
        this.pushStateToDescriptor();

        if (this.phase == StormLifecyclePhase.ACTIVE || this.phase == StormLifecyclePhase.DISSIPATING) {
            if (gameTime - this.lastAmbientWindTick >= FLOW_FIELD_INTERVAL_TICKS) {
                this.lastAmbientWindTick = gameTime;
                this.applyAmbientWind(level);
            }
            if (gameTime - this.lastDemolitionTick >= DEMOLITION_INTERVAL_TICKS && this.normalizedIntensity >= 0.35F) {
                this.lastDemolitionTick = gameTime;
                this.demolishBlocks(level);
                this.playDemolitionSound(level);
            }
        }
    }

    public void tickClient() {
        this.ageTicks++;
        this.clientPreviousRenderPosition = this.clientRenderPosition;
        this.clientPreviousRenderBottomY = this.clientRenderBottomY;
        this.clientPreviousRenderHeight = this.clientRenderHeight;
        this.clientPreviousRenderRadius = this.clientRenderRadius;

        float follow = 0.38F;
        this.clientRenderPosition = this.clientRenderPosition.lerp(this.position, follow);
        this.clientRenderBottomY = Mth.lerp(follow, this.clientRenderBottomY, this.visualBottomY);
        this.clientRenderHeight = Mth.lerp(follow, this.clientRenderHeight, this.visualHeight);
        this.clientRenderRadius = Mth.lerp(follow, this.clientRenderRadius, this.radius);
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
                this.stormLevel,
                this.recentDebrisScore,
                this.phase
        );
    }

    public void applySnapshot(TornadoSnapshot snapshot, @Nullable CloudRegion region) {
        boolean snapToTarget = this.ageTicks <= 1 || this.clientRenderPosition.distanceToSqr(snapshot.position()) > 1024.0D;
        this.position = snapshot.position();
        this.radius = snapshot.radius();
        this.visualBottomY = snapshot.visualBottomY();
        this.visualHeight = snapshot.visualHeight();
        this.wind = new WindVector(snapshot.windSpeed(), snapshot.windAngle(), snapshot.windGust());
        this.normalizedIntensity = snapshot.normalizedIntensity();
        this.stormLevel = StormSeverityScale.clamp(snapshot.stormLevel());
        this.recentDebrisScore = snapshot.recentDebrisScore();
        this.phase = snapshot.phase();
        this.cloudRegion = region;
        this.anchorX = (float) this.position.x;
        this.anchorZ = (float) this.position.z;
        if (snapToTarget) {
            this.clientPreviousRenderPosition = this.position;
            this.clientRenderPosition = this.position;
            this.clientPreviousRenderBottomY = this.visualBottomY;
            this.clientRenderBottomY = this.visualBottomY;
            this.clientPreviousRenderHeight = this.visualHeight;
            this.clientRenderHeight = this.visualHeight;
            this.clientPreviousRenderRadius = this.radius;
            this.clientRenderRadius = this.radius;
        }
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
        float stormBias = 0.18F + StormSeverityScale.toNormalized(this.stormLevel) * 0.82F;
        this.radius = Mth.lerp(growth, this.maxRadius * (0.26F + stormBias * 0.12F), this.maxRadius * (0.90F + stormBias * 0.22F));
        this.visualHeight = Mth.lerp(growth, this.targetVisualHeight * (0.30F + stormBias * 0.08F), this.targetVisualHeight * (0.92F + stormBias * 0.15F));
        this.angularSpeed = 0.08F + growth * 0.16F + StormSeverityScale.toNormalized(this.stormLevel) * 0.05F;
    }

    private void updateMovement(ServerLevel level, long gameTime) {
        Vec3 updated = StormMotionModel.advanceTornado(
                level,
                this.id,
                this.position,
                this.motion,
                this.wind,
                Math.max(this.normalizedIntensity, 0.08F),
                this.stormLevel,
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

        Vec3 anchor = this.getInteractionAnchor(serverLevel);
        double influence = this.getWindfieldWidth() * 1.25D + AMBIENT_WIND_INFLUENCE_EXTENSION;
        double minY = anchor.y + WIND_EFFECT_VERTICAL_MIN_OFFSET;
        double maxY = anchor.y + Math.max(90.0D, this.visualHeight * 0.60D);
        AABB box = new AABB(
                anchor.x - influence, minY,
                anchor.z - influence, anchor.x + influence,
                maxY, anchor.z + influence
        );

        for (Entity entity : serverLevel.getEntities(null, box)) {
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            if (StormShieldManager.isProtected(serverLevel, entity.position())) {
                continue;
            }
            this.pullEntity(entity, 2.45F);
        }
    }

    private void demolishBlocks(ServerLevel level) {
        Vec3 anchor = this.getInteractionAnchor(level);
        BlockPos center = BlockPos.containing(anchor);
        float windfieldWidth = this.getWindfieldWidth();
        float destructionRadius = Math.max(this.radius + 6.0F, windfieldWidth * 0.68F);
        int intRadius = Mth.ceil(destructionRadius);
        double outerSq = destructionRadius * destructionRadius;
        double innerSq = Math.max(0.0F, destructionRadius - 5.0F) * Math.max(0.0F, destructionRadius - 5.0F);
        double band = Math.max(1.0, outerSq - innerSq);
        double invBand = 1.0 / band;
        int topScan = Math.max(24, intRadius + 22);
        BlockPos min = center.offset(-intRadius - DEBRIS_RANGE_EXTENSION, -1, -intRadius - DEBRIS_RANGE_EXTENSION);
        BlockPos max = center.offset(intRadius + DEBRIS_RANGE_EXTENSION, topScan, intRadius + DEBRIS_RANGE_EXTENSION);

        RandomSource random = RandomSource.create(this.id.getLeastSignificantBits() ^ this.ageTicks);
        it.unimi.dsi.fastutil.longs.LongArrayList toDestroy = new it.unimi.dsi.fastutil.longs.LongArrayList(2048);
        it.unimi.dsi.fastutil.longs.LongArrayList toDestroyGlass = new it.unimi.dsi.fastutil.longs.LongArrayList(2048);
        it.unimi.dsi.fastutil.longs.LongArrayList toDestroyWeak = new it.unimi.dsi.fastutil.longs.LongArrayList(2048);
        it.unimi.dsi.fastutil.longs.LongArrayList toScourGrass = new it.unimi.dsi.fastutil.longs.LongArrayList(1024);
        float stormFactor = StormSeverityScale.toNormalized(this.stormLevel);
        int scannedBlocks = 0;
        int eligibleBlocks = 0;

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (StormShieldManager.isProtected(level, pos)) {
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
                scannedBlocks++;
                double dx = pos.getX() + 0.5D - anchor.x;
                double dz = pos.getZ() + 0.5D - anchor.z;
                double horizontalDistSq = dx * dx + dz * dz;
                if (horizontalDistSq > outerSq * 1.08D) {
                    continue;
                }
                float windEffect = this.getWindEffect(pos.getCenter());
                if (windEffect < 26.0F) {
                    continue;
                }
                eligibleBlocks++;
                if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                    this.queueTreeCluster(level, pos, toDestroy);
                } else if (AtmosphereUtils.isGlass(state)) {
                    if (horizontalDistSq > outerSq) {
                        continue;
                    }

                    float pMax = 0.15F + this.normalizedIntensity * 0.35F;
                    double t = Mth.clamp((outerSq - horizontalDistSq) * invBand, 0.0, 1.0);
                    float p = (float) (t * pMax);
                    if (random.nextFloat() < p) {
                        toDestroyGlass.add(pos.asLong());
                    }
                } else if (state.getFluidState().isEmpty()) {
                    if (state.is(Blocks.GRASS_BLOCK)) {
                        float scourChance = Mth.clamp((windEffect - 34.0F) / 90.0F, 0.0F, 1.0F) * (0.14F + this.normalizedIntensity * 0.22F + stormFactor * 0.16F);
                        boolean forceScour = windEffect >= 88.0F || horizontalDistSq <= innerSq * 0.75D;
                        if (forceScour || random.nextFloat() < scourChance) {
                            toScourGrass.add(pos.asLong());
                        }
                    }
                    if (isVegetationBlock(state) || isSimpleStructureBlock(state) || isWeakBlock(state, level, pos, stormFactor)) {
                        float chance = Mth.clamp((windEffect - 44.0F) / 86.0F, 0.0F, 1.0F) * (0.12F + this.normalizedIntensity * 0.16F + stormFactor * 0.14F);
                        boolean forceBreak = windEffect >= 96.0F || horizontalDistSq <= innerSq * 0.70D;
                        if (forceBreak || random.nextFloat() < chance) {
                            toDestroyWeak.add(pos.asLong());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        int perTick = 640;
        if (!toDestroy.isEmpty()) {
            AsyncAtmosphereService.runOnMainThread(() -> this.processLeafLogDestruction(level, toDestroy, perTick, 0));
        }
        if (!toDestroyWeak.isEmpty()) {
            AsyncAtmosphereService.runOnMainThread(() -> this.processWeakDestruction(level, toDestroyWeak, perTick, 0, stormFactor));
        }
        if (!toScourGrass.isEmpty()) {
            AsyncAtmosphereService.runOnMainThread(() -> this.processGrassScouring(level, toScourGrass, perTick, 0));
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

        if (ProjectAtmosphere.DEBUG_MODE && this.ageTicks % DEMOLITION_DEBUG_LOG_INTERVAL_TICKS == 0) {
            ProjectAtmosphere.LOGGER.info(
                    "[TornadoDebug] demolish id={} center=({}, {}, {}) radius={} windfield={} scanned={} eligible={} leafLog={} weak={} grass={} glass={}",
                    this.id,
                    Mth.floor(anchor.x),
                    Mth.floor(anchor.y),
                    Mth.floor(anchor.z),
                    destructionRadius,
                    windfieldWidth,
                    scannedBlocks,
                    eligibleBlocks,
                    toDestroy.size(),
                    toDestroyWeak.size(),
                    toScourGrass.size(),
                    toDestroyGlass.size()
            );
        }
    }

    private void processLeafLogDestruction(ServerLevel level,
                                           it.unimi.dsi.fastutil.longs.LongArrayList list,
                                           int perTick,
                                           int startIndex) {
        if (startIndex >= list.size()) {
            return;
        }

        int end = Math.min(startIndex + perTick, list.size());
        for (int i = startIndex; i < end; i++) {
            BlockPos pos = BlockPos.of(list.getLong(i));
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (StormShieldManager.isProtected(level, pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS))) {
                continue;
            }
            level.destroyBlock(pos, false);
        }

        if (end < list.size()) {
            level.getServer().execute(() -> this.processLeafLogDestruction(level, list, perTick, end));
        }
    }

    private void processWeakDestruction(ServerLevel level,
                                        it.unimi.dsi.fastutil.longs.LongArrayList list,
                                        int perTick,
                                        int startIndex,
                                        float stormFactor) {
        if (startIndex >= list.size()) {
            return;
        }

        int end = Math.min(startIndex + perTick, list.size());
        for (int i = startIndex; i < end; i++) {
            BlockPos pos = BlockPos.of(list.getLong(i));
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (StormShieldManager.isProtected(level, pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (!(isVegetationBlock(state) || isSimpleStructureBlock(state) || isWeakBlock(state, level, pos, stormFactor))) {
                continue;
            }
            level.destroyBlock(pos, false);
        }

        if (end < list.size()) {
            level.getServer().execute(() -> this.processWeakDestruction(level, list, perTick, end, stormFactor));
        }
    }

    private void processGrassScouring(ServerLevel level,
                                      it.unimi.dsi.fastutil.longs.LongArrayList list,
                                      int perTick,
                                      int startIndex) {
        if (startIndex >= list.size()) {
            return;
        }

        int end = Math.min(startIndex + perTick, list.size());
        for (int i = startIndex; i < end; i++) {
            BlockPos pos = BlockPos.of(list.getLong(i));
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (StormShieldManager.isProtected(level, pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.GRASS_BLOCK)) {
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            }
        }

        if (end < list.size()) {
            level.getServer().execute(() -> this.processGrassScouring(level, list, perTick, end));
        }
    }

    private void playDemolitionSound(Level level) {
        BlockPos center = level instanceof ServerLevel serverLevel
                ? BlockPos.containing(this.getInteractionAnchor(serverLevel))
                : BlockPos.containing(this.position);
        level.playLocalSound(
                center.getX(), center.getY(), center.getZ(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.WEATHER,
                1.0F + this.normalizedIntensity,
                0.6F + level.getRandom().nextFloat() * 0.3F,
                false
        );
    }

    private void refreshStormLevel(ServerLevel level, long gameTime) {
        int strongest = this.cloudRegion == null ? StormSeverityScale.MIN_LEVEL : StormSeverityScale.clamp(net.Gabou.projectatmosphere.modules.core.CloudLibrary.getSeverityFromRessourceLocation(this.cloudRegion.getCloudTypeId()));
        var regionKey = net.Gabou.projectatmosphere.util.RegionInstanceKey.from(BlockPos.containing(this.position));
        strongest = Math.max(strongest, StormSeverityScale.resolve(level, regionKey, gameTime));
        this.stormLevel = strongest;
        this.targetIntensity = defaultTargetIntensity(this.maxRadius, this.wind, this.stormLevel);
    }

    @Nullable
    private TornadoDescriptor findDescriptor() {
        if (!(this.cloudRegion instanceof ITornadoRegion tornadoRegion)) {
            return null;
        }
        return tornadoRegion.findTornado(this.id);
    }

    private static float defaultTargetIntensity(float radius, WindVector wind, int stormLevel) {
        float radiusFactor = Mth.clamp((radius - 5.0F) / 20.0F, 0.0F, 1.0F);
        float windFactor = Mth.clamp((wind.baseSpeed() - 12.0F) / 28.0F, 0.0F, 1.0F);
        float stormFactor = StormSeverityScale.toNormalized(stormLevel);
        return Mth.clamp(0.18F + radiusFactor * 0.28F + windFactor * 0.18F + stormFactor * 0.42F, 0.18F, 1.0F);
    }

    private static boolean isVegetationBlock(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS)
                || state.is(Blocks.VINE)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.canBeReplaced();
    }

    private static boolean isSimpleStructureBlock(BlockState state) {
        return state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.FENCES)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.WOOL)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.CAMPFIRES);
    }

    private static boolean isWeakBlock(BlockState state, Level level, BlockPos pos, float stormFactor) {
        float destroySpeed = state.getDestroySpeed(level, pos);
        if (destroySpeed < 0.0F) {
            return false;
        }
        float threshold = 1.0F + stormFactor * 1.4F;
        return destroySpeed <= threshold;
    }

    private float getWindfieldWidth() {
        return Math.max(this.radius * (1.65F + StormSeverityScale.toNormalized(this.stormLevel) * 0.45F), 28.0F);
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
        if (StormShieldManager.isProtected(entity.level(), entity.position())) {
            this.capturedEntities.remove(entity.getId());
            return;
        }
        Vec3 anchor = entity.level() instanceof ServerLevel serverLevel
                ? this.getInteractionAnchor(serverLevel)
                : new Vec3(this.position.x, this.visualBottomY, this.position.z);
        float windfieldWidth = this.getWindfieldWidth();
        CapturedEntityState captured = this.capturedEntities.get(entity.getId());
        int terrainY = entity.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                entity.blockPosition().getX(),
                entity.blockPosition().getZ()
        ) - 1;
        if (entity.getY() + 1.5D < terrainY) {
            return;
        }

        Vec3 targetPos = new Vec3(anchor.x, entity.position().y, anchor.z);
        double dist = entity.position().distanceTo(targetPos);
        double maxInfluenceDistance = windfieldWidth * (captured != null ? 1.28D : OUTER_FLOW_RADIUS_FACTOR);
        if (dist > maxInfluenceDistance) {
            return;
        }

        Vec3 relativePos = entity.position().subtract(anchor);
        double heightDifference = entity.position().y - anchor.y;
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
        float heightNorm = Mth.clamp((float) ((entity.getY() - anchor.y) / Math.max(this.visualHeight, 1.0F)), 0.0F, 1.15F);
        float outerReach = 1.0F - smoothStep(0.68F, 1.10F, radialNorm);
        float shellZone = 1.0F - Mth.clamp(Math.abs(radialNorm - MID_SHELL_CENTER) / MID_SHELL_WIDTH, 0.0F, 1.0F);
        float coreZone = 1.0F - smoothStep(0.05F, CORE_ZONE_END, radialNorm);
        float plumeZone = smoothStep(PLUME_FLOW_START, 0.98F, heightNorm);
        boolean shouldCapture = dist <= windfieldWidth * CAPTURE_ENTRY_RADIUS
                || (shellZone > 0.35F && windEffect >= 48.0D)
                || (coreZone > 0.35F && windEffect >= 42.0D);
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
            captured.orbitAngle += (0.11F + this.normalizedIntensity * 0.10F + shellZone * 0.12F + transportProgress * 0.09F)
                    * (1.0F - plumeZone * 0.22F)
                    * captured.orbitBias
                    * CAPTURED_ENTITY_ORBIT_SPEED_SCALE;

            double desiredRadius = windfieldWidth * captured.radialBand;
            double desiredHeight = anchor.y + this.visualHeight * Mth.clamp(
                    heightNorm + 0.04F + captured.liftBias * 0.08F + plumeZone * 0.08F + captureProgress * 0.16F + transportProgress * 0.28F,
                    0.04F,
                    1.10F
            );
            Vec3 orbitTarget = new Vec3(
                    Math.cos(captured.orbitAngle) * desiredRadius,
                    desiredHeight,
                    Math.sin(captured.orbitAngle) * desiredRadius
            ).add(anchor.x, 0.0D, anchor.z);
            Vec3 towardOrbit = orbitTarget.subtract(entity.position());
            Vec3 orbitCorrection = towardOrbit.lengthSqr() > 1.0E-4 ? towardOrbit.normalize() : Vec3.ZERO;
            double inwardStrength = captured.expelling
                    ? -0.16D
                    : Mth.lerp(captureProgress, 2.45D + outerReach * 1.20D + shellZone * 0.38D, 0.54D + shellZone * 0.24D - coreZone * 0.04D);
            double liftStrength = captured.expelling
                    ? effectStrength * (0.52D + plumeZone * 0.32D)
                    : (0.08D + captured.liftBias * 0.18D + plumeZone * 0.16D + captureProgress * 0.24D + transportProgress * 0.88D) * effectStrength;
            double tangentialStrength = captured.expelling
                    ? effectStrength * (0.36D + plumeZone * 0.14D) * CAPTURED_ENTITY_TANGENTIAL_SCALE
                    : effectStrength * (0.20D + shellZone * 0.82D + captured.orbitBias * 0.24D + transportProgress * 0.64D) * CAPTURED_ENTITY_TANGENTIAL_SCALE;
            double ejectFactor = captured.expelling
                    ? 1.35D + plumeZone * 0.78D
                    : 0.0D;
            Vec3 outward = inward.scale(-1.0D);

            add = orbitCorrection.scale(effectStrength * 1.15D)
                    .add(rotational.scale(tangentialStrength))
                    .add(inward.scale(effectStrength * inwardStrength))
                    .add(outward.scale(effectStrength * ejectFactor))
                    .add(0.0D, liftStrength * ENTITY_LIFT_VECTOR_SCALE, 0.0D)
                    .scale(captured.expelling ? 0.125D : 0.155D);
            releaseCapture = captured.expelling && (heightNorm > 1.08F || dist > windfieldWidth * 1.34F);
        } else {
            double suctionStrength = 1.95D + outerReach * 2.85D + shellZone * 0.28D - coreZone * 0.10D;
            double tangentialStrength = (0.04D + shellZone * 0.17D + plumeZone * 0.03D) * UNCAPTURED_ENTITY_TANGENTIAL_SCALE;
            double liftStrength = 0.02D + shellZone * 0.05D + plumeZone * 0.08D;

            add = inward.scale(effectStrength * suctionStrength)
                    .add(rotational.scale(effectStrength * tangentialStrength))
                    .add(0.0D, effectStrength * liftStrength * ENTITY_LIFT_VECTOR_SCALE, 0.0D)
                    .scale(0.094D + this.normalizedIntensity * 0.034D);
        }
        if (captured != null) {
            Vec3 current = entity.getDeltaMovement();
            Vec3 damped = captured.expelling
                    ? new Vec3(current.x * 0.80D, Math.max(current.y * 0.90D, -0.04D), current.z * 0.80D)
                    : new Vec3(current.x * 0.42D, Math.max(current.y * 0.88D, -0.02D), current.z * 0.42D);
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
        float liftBias = 0.72F + (float) StormMotionModel.noise01(this.id, this.ageTicks + entity.getId() * 3L, 0.07F) * 0.58F;
        return new CapturedEntityState(angle, band, orbitBias, liftBias, this.ageTicks, 0);
    }

    private void queueTreeCluster(ServerLevel level, BlockPos origin, it.unimi.dsi.fastutil.longs.LongArrayList list) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    BlockPos sample = origin.offset(dx, dy, dz);
                    if (!level.isLoaded(sample) || StormShieldManager.isProtected(level, sample)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(sample);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                        list.add(sample.asLong());
                    }
                }
            }
        }
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
        float composite = Mth.clamp(this.normalizedIntensity * 0.72F + StormSeverityScale.toNormalized(this.stormLevel) * 0.28F, 0.0F, 1.0F);
        return Mth.lerp(composite, MIN_EFFECTIVE_WIND, MAX_EFFECTIVE_WIND);
    }

    private void applyWaterPenalty(float waterExposure) {
        if (waterExposure <= WATER_PENALTY_THRESHOLD) {
            return;
        }

        float penalty = Mth.clamp((waterExposure - WATER_PENALTY_THRESHOLD) / (1.0F - WATER_PENALTY_THRESHOLD), 0.0F, 1.0F);
        this.targetIntensity = Math.max(0.10F, this.targetIntensity * (1.0F - penalty * 0.72F));
        if (this.phase == StormLifecyclePhase.ACTIVE) {
            this.phaseTicks += 1 + Mth.floor(penalty * 3.0F);
        } else if (this.phase == StormLifecyclePhase.FORMING) {
            this.phaseTicks += Mth.floor(penalty * 2.0F);
        }

        if (waterExposure >= WATER_FORCED_DISSIPATION_THRESHOLD && this.phase == StormLifecyclePhase.ACTIVE && this.ageTicks > 80) {
            this.markDissipating();
        }
    }

    private Vec3 getInteractionAnchor(ServerLevel level) {
        return new Vec3(this.position.x, this.sampleTerrainSurfaceY(level), this.position.z);
    }

    private float sampleTerrainSurfaceY(ServerLevel level) {
        int centerX = Mth.floor(this.position.x);
        int centerZ = Mth.floor(this.position.z);
        int sampleOffset = Math.max(2, Mth.ceil(Math.min(this.getWindfieldWidth() * 0.18F, 10.0F)));

        float highestSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ) - 1.0F;
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX + sampleOffset, centerZ) - 1.0F);
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX - sampleOffset, centerZ) - 1.0F);
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ + sampleOffset) - 1.0F);
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ - sampleOffset) - 1.0F);
        return highestSurface;
    }

    private float sampleWaterExposure(ServerLevel level) {
        double sampleRadius = Math.max(6.0D, this.getWindfieldWidth() * 0.45D);
        double[][] offsets = {
                {0.0D, 0.0D},
                {sampleRadius, 0.0D},
                {-sampleRadius, 0.0D},
                {0.0D, sampleRadius},
                {0.0D, -sampleRadius},
                {sampleRadius * 0.7D, sampleRadius * 0.7D},
                {sampleRadius * 0.7D, -sampleRadius * 0.7D},
                {-sampleRadius * 0.7D, sampleRadius * 0.7D},
                {-sampleRadius * 0.7D, -sampleRadius * 0.7D}
        };

        int waterSamples = 0;
        for (double[] offset : offsets) {
            int x = Mth.floor(this.position.x + offset[0]);
            int z = Mth.floor(this.position.z + offset[1]);
            int terrainY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos surfacePos = new BlockPos(x, terrainY - 1, z);
            BlockPos belowPos = surfacePos.below();
            BlockState surface = level.getBlockState(surfacePos);
            BlockState below = level.getBlockState(belowPos);
            if (surface.is(Blocks.WATER)
                    || below.is(Blocks.WATER)
                    || surface.getFluidState().is(FluidTags.WATER)
                    || below.getFluidState().is(FluidTags.WATER)) {
                waterSamples++;
            }
        }
        return waterSamples / (float) offsets.length;
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

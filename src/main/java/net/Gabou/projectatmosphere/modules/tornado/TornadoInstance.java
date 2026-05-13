package net.Gabou.projectatmosphere.modules.tornado;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.ITornadoRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.Gabou.projectatmosphere.modules.weather.StormMotionModel;
import net.Gabou.projectatmosphere.modules.weather.StormSeverityScale;
import net.Gabou.projectatmosphere.modules.weather.StormShieldManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TornadoInstance {
    public static final double AMBIENT_WIND_INFLUENCE_EXTENSION = 15.0D;
    public static final double WIND_SPEED_SCALING_FACTOR = 0.05D;
    public static final double WIND_EFFECT_VERTICAL_MAX_OFFSET = 50.0D;
    public static final double WIND_EFFECT_VERTICAL_MIN_OFFSET = -5.0D;
    private static final int MINIMUM_PERSISTENCE_TICKS = 20 * 120;
    private static final int MINIMUM_ACTIVE_TICKS = 20 * 120;
    private static final int MAXIMUM_ACTIVE_TICKS = 20 * 600;
    private static final int MINIMUM_FORMATION_TICKS = 20 * 6;
    private static final int MAXIMUM_FORMATION_TICKS = 20 * 20;
    private static final int MINIMUM_DISSIPATION_TICKS = 20 * 8;
    private static final int MAXIMUM_DISSIPATION_TICKS = 20 * 40;
    private static final int FLOW_FIELD_INTERVAL_TICKS = 1;
    private static final int DEMOLITION_INTERVAL_TICKS = 4;
    private static final float MIN_EFFECTIVE_WIND = 73.0F;
    private static final float MAX_EFFECTIVE_WIND = 260.0F;
    private static final double OUTER_ENTITY_INFLUENCE_PADDING = 16.0D;
    private static final double ENTITY_MIN_VERTICAL_RANGE = -8.0D;
    private static final double ENTITY_MAX_VERTICAL_PADDING = 20.0D;
    private static final double ENTITY_RELEASE_HEIGHT_PADDING = 10.0D;
    private static final float CAPTURE_RADIUS_FACTOR = 0.84F;
    private static final float CORE_RADIUS_FACTOR = 0.34F;
    private static final float OUTER_ORBIT_RADIUS_FACTOR = 0.60F;
    private static final float INNER_ORBIT_RADIUS_FACTOR = 0.30F;
    private static final float CAPTURE_HYSTERESIS_FACTOR = 1.18F;
    private static final int CAPTURE_FULL_TICKS = 24;
    private static final int CAPTURE_ASCENT_TICKS = 90;
    private static final int CAPTURE_RELEASE_TICKS = 220;
    private static final float BASE_SUCTION_FORCE = 0.13F;
    private static final float BASE_TANGENTIAL_FORCE = 0.11F;
    private static final float BASE_LIFT_FORCE = 0.12F;
    private static final float CAPTURED_SUCTION_FORCE = 0.25F;
    private static final float CAPTURED_TANGENTIAL_FORCE = 0.23F;
    private static final float CAPTURED_LIFT_FORCE = 0.34F;
    private static final float WATER_PENALTY_THRESHOLD = 0.20F;
    private static final int CLOUD_DETACH_GRACE_TICKS = 20 * 30;
    private static final float CLIENT_POSITION_INTERPOLATION = 0.18F;
    private static final float CLIENT_SHAPE_INTERPOLATION = 0.22F;
    private static final double CLIENT_SNAPSHOT_INTERVAL_TICKS = 5.0D;
    private static final double CLIENT_VELOCITY_TRACKING = 0.45D;
    private static final double CLIENT_VELOCITY_DAMPING = 0.84D;
    private static final double CLIENT_EXTRAPOLATION_TICKS = 1.35D;
    private static final int TREE_CLUSTER_HORIZONTAL_RADIUS = 4;
    private static final int TREE_CLUSTER_BELOW = 4;
    private static final int TREE_CLUSTER_ABOVE = 20;
    private static final int TREE_CLUSTER_VISIT_LIMIT = 512;
    private static final float MAX_DEBRIS_ENTITY_SPAWN_CHANCE = 0.46F;
    private static final int BASE_MAX_DEBRIS_ENTITY_SPAWNS = 8;
    private static final int ENTITY_DAMAGE_INTERVAL_TICKS = 8;
    private static final float MOVEMENT_ROUTE_LEASH_RADIUS = 150.0F;
    private static final double MOVEMENT_ROUTE_REACHED_DISTANCE_SQR = 36.0D;
    private static final float MOVEMENT_HEADING_BLEND = 0.010F;
    private static final float MOVEMENT_SPEED_BLEND = 0.10F;
    private static final double MOVEMENT_VECTOR_BLEND = 0.16D;
    private static final double MOVEMENT_AMBIENT_SCALE = 0.0035D;
    private static final int MOVEMENT_REPLAN_ON_AVOIDANCE_TICKS = 24;

    private final UUID id;
    public Vec3 position;
    public float radius;
    public WindVector wind;
    private final float maxRadius;
    private float targetVisualHeight;
    private float visualBottomY;
    private float visualHeight;
    private float angularSpeed;
    private float normalizedIntensity;
    private float targetIntensity;
    private int stormLevel;
    private float headingRadians;
    private float targetHeadingRadians;
    private Vec3 motion = Vec3.ZERO;
    private float plannedMoveSpeed;
    private float targetMoveSpeed;
    private int routeTicksRemaining;
    @Nullable
    private Vec3 routeWaypoint;
    private boolean descriptorMissing;
    private int ageTicks;
    private int phaseTicks;
    private int formationTicks;
    private int activeTicks;
    private int dissipationTicks;
    private int detachedTicks;
    private long spawnGameTime;
    private long lastAmbientWindTick = 0;
    private long lastDemolitionTick = 0;
    private float anchorX;
    private float anchorZ;
    private final boolean requiresCloudAttachment;
    private StormLifecyclePhase phase;
    private float recentDebrisScore;
    private Vec3 clientPreviousRenderPosition;
    private Vec3 clientRenderPosition;
    private Vec3 clientTargetPosition;
    private Vec3 clientTargetVelocity;
    private float clientPreviousRenderBottomY;
    private float clientRenderBottomY;
    private float clientTargetBottomY;
    private float clientPreviousRenderHeight;
    private float clientRenderHeight;
    private float clientTargetHeight;
    private float clientPreviousRenderRadius;
    private float clientRenderRadius;
    private float clientTargetRadius;
    private final Map<Integer, CapturedEntityState> capturedEntities = new HashMap<>();
    private int debugEligibleEntityCount;
    private int debugCapturedEntityCount;
    private int debugForceSampleCount;
    private double debugPullForceSum;
    private double debugUpwardForceSum;
    private double debugPullForceMax;
    private double debugUpwardForceMax;
    private float debugDestructionSweepRadius;
    private int debugDestructionCandidateBlockCount;
    private int debugDestroyedBlockCount;
    private int debugDestroyedLeafLogCount;
    private int debugDestroyedWeakCount;
    private int debugDestroyedGrassCount;
    private int debugDestroyedGlassCount;

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
        this(id, position, radius, wind, angularSpeed, visualBottomY, visualHeight, cloudRegion, stormLevel, true);
    }

    public TornadoInstance(UUID id, Vec3 position, float radius, WindVector wind, float angularSpeed,
                           float visualBottomY, float visualHeight, @Nullable CloudRegion cloudRegion, int stormLevel,
                           boolean requiresCloudAttachment) {
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
        this.requiresCloudAttachment = requiresCloudAttachment;
        this.anchorX = (float) position.x;
        this.anchorZ = (float) position.z;
        this.phase = StormLifecyclePhase.FORMING;
        this.targetIntensity = defaultTargetIntensity(radius, wind, this.stormLevel);
        this.normalizedIntensity = Math.max(0.18F, this.targetIntensity * 0.35F);
        this.headingRadians = wind.angleRadians();
        this.targetHeadingRadians = this.headingRadians;
        this.plannedMoveSpeed = 0.04F + this.targetIntensity * 0.05F;
        this.targetMoveSpeed = this.plannedMoveSpeed;
        this.routeTicksRemaining = 0;
        this.routeWaypoint = null;
        float persistenceFactor = Mth.clamp(
                this.targetIntensity * 0.65F + StormSeverityScale.toNormalized(this.stormLevel) * 0.35F,
                0.0F,
                1.0F
        );
        this.formationTicks = Mth.floor(Mth.lerp(persistenceFactor, MINIMUM_FORMATION_TICKS, MAXIMUM_FORMATION_TICKS));
        this.activeTicks = Mth.floor(Mth.lerp(persistenceFactor, MINIMUM_ACTIVE_TICKS, MAXIMUM_ACTIVE_TICKS));
        this.dissipationTicks = Mth.floor(Mth.lerp(persistenceFactor, MINIMUM_DISSIPATION_TICKS, MAXIMUM_DISSIPATION_TICKS));
        this.applyIntensityToVisuals();
        this.clientPreviousRenderPosition = position;
        this.clientRenderPosition = position;
        this.clientTargetPosition = position;
        this.clientTargetVelocity = Vec3.ZERO;
        this.clientPreviousRenderBottomY = this.visualBottomY;
        this.clientRenderBottomY = this.visualBottomY;
        this.clientTargetBottomY = this.visualBottomY;
        this.clientPreviousRenderHeight = this.visualHeight;
        this.clientRenderHeight = this.visualHeight;
        this.clientTargetHeight = this.visualHeight;
        this.clientPreviousRenderRadius = this.radius;
        this.clientRenderRadius = this.radius;
        this.clientTargetRadius = this.radius;
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
        this.detachedTicks = cloudRegion == null ? this.detachedTicks : 0;
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
        return this.getVisualSpin(0.0F);
    }

    public float getVisualSpin(float partialTick) {
        float elapsedTicks = this.ageTicks + Mth.clamp(partialTick, 0.0F, 1.0F);
        return elapsedTicks * (0.004F + this.angularSpeed * 0.16F);
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

    public void activateImmediately() {
        this.phase = StormLifecyclePhase.ACTIVE;
        this.phaseTicks = 0;
        this.normalizedIntensity = this.targetIntensity;
        this.applyIntensityToVisuals();
    }

    public boolean isDead() {
        return this.phase.isTerminal();
    }

    public void updateCloudAttachment(boolean attached) {
        if (!this.requiresCloudAttachment) {
            this.detachedTicks = 0;
            return;
        }
        if (attached) {
            this.detachedTicks = 0;
            return;
        }

        this.detachedTicks++;
        if (this.detachedTicks >= CLOUD_DETACH_GRACE_TICKS
                && this.ageTicks >= MINIMUM_PERSISTENCE_TICKS
                && this.phase != StormLifecyclePhase.DISSIPATING) {
            this.markDissipating();
        }
    }

    public int getDetachedTicks() {
        return this.detachedTicks;
    }

    public RuntimeDebugSnapshot getRuntimeDebugSnapshot() {
        double averagePullForce = this.debugForceSampleCount <= 0 ? 0.0D : this.debugPullForceSum / this.debugForceSampleCount;
        double averageUpwardForce = this.debugForceSampleCount <= 0 ? 0.0D : this.debugUpwardForceSum / this.debugForceSampleCount;
        return new RuntimeDebugSnapshot(
                this.id,
                this.phase,
                this.normalizedIntensity,
                this.debugEligibleEntityCount,
                this.debugCapturedEntityCount,
                averagePullForce,
                this.debugPullForceMax,
                averageUpwardForce,
                this.debugUpwardForceMax,
                this.debugDestructionSweepRadius,
                this.debugDestructionCandidateBlockCount,
                this.debugDestroyedBlockCount,
                this.debugDestroyedLeafLogCount,
                this.debugDestroyedWeakCount,
                this.debugDestroyedGrassCount,
                this.debugDestroyedGlassCount
        );
    }

    public void tickServer(ServerLevel level, long gameTime) {
        this.ageTicks++;
        this.phaseTicks++;
        if (this.spawnGameTime == 0L) {
            this.spawnGameTime = gameTime;
        }

        this.resetRuntimeDebugStats();

        WindVector sampledWind = ForecastOrchestrator.getWind(level, BlockPos.containing(this.position), gameTime);
        this.wind = sampledWind;
        this.refreshStormLevel(level, gameTime);
        float waterExposure = this.sampleWaterExposure(level);
        this.applyWaterPenalty(waterExposure);
        this.recentDebrisScore = Math.max(0.0F, this.recentDebrisScore - 0.015F);
        this.pruneCapturedEntities(level);
        this.tickLifecycle();
        this.updateMovement(level, gameTime);
        this.updateGroundedVisualBase(level);
        this.pushStateToDescriptor();

        if (!this.phase.isTerminal() && this.normalizedIntensity >= 0.08F) {
            if (gameTime - this.lastAmbientWindTick >= FLOW_FIELD_INTERVAL_TICKS) {
                this.lastAmbientWindTick = gameTime;
                this.applyAmbientWind(level);
            }
            if (gameTime - this.lastDemolitionTick >= DEMOLITION_INTERVAL_TICKS && this.normalizedIntensity >= 0.18F) {
                this.lastDemolitionTick = gameTime;
                if (this.demolishBlocks(level)) {
                    this.playDemolitionSound(level);
                }
            }
        }
    }

    public void tickClient() {
        this.ageTicks++;
        this.clientPreviousRenderPosition = this.clientRenderPosition;
        this.clientPreviousRenderBottomY = this.clientRenderBottomY;
        this.clientPreviousRenderHeight = this.clientRenderHeight;
        this.clientPreviousRenderRadius = this.clientRenderRadius;

        Vec3 predictedTarget = this.clientTargetPosition.add(this.clientTargetVelocity.scale(CLIENT_EXTRAPOLATION_TICKS));
        this.clientRenderPosition = this.clientRenderPosition.lerp(predictedTarget, CLIENT_POSITION_INTERPOLATION);
        this.clientRenderBottomY = Mth.lerp(CLIENT_SHAPE_INTERPOLATION, this.clientRenderBottomY, this.clientTargetBottomY);
        this.clientRenderHeight = Mth.lerp(CLIENT_SHAPE_INTERPOLATION, this.clientRenderHeight, this.clientTargetHeight);
        this.clientRenderRadius = Mth.lerp(CLIENT_SHAPE_INTERPOLATION, this.clientRenderRadius, this.clientTargetRadius);
        this.clientTargetVelocity = this.clientTargetVelocity.scale(CLIENT_VELOCITY_DAMPING);
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
        Vec3 previousTargetPosition = this.clientTargetPosition;
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
            this.clientTargetPosition = this.position;
            this.clientTargetVelocity = Vec3.ZERO;
            this.clientPreviousRenderBottomY = this.visualBottomY;
            this.clientRenderBottomY = this.visualBottomY;
            this.clientTargetBottomY = this.visualBottomY;
            this.clientPreviousRenderHeight = this.visualHeight;
            this.clientRenderHeight = this.visualHeight;
            this.clientTargetHeight = this.visualHeight;
            this.clientPreviousRenderRadius = this.radius;
            this.clientRenderRadius = this.radius;
            this.clientTargetRadius = this.radius;
            return;
        }

        Vec3 snapshotVelocity = this.position.subtract(previousTargetPosition).scale(1.0D / CLIENT_SNAPSHOT_INTERVAL_TICKS);
        this.clientTargetPosition = this.position;
        this.clientTargetVelocity = this.clientTargetVelocity.lerp(snapshotVelocity, CLIENT_VELOCITY_TRACKING);
        this.clientTargetBottomY = this.visualBottomY;
        this.clientTargetHeight = this.visualHeight;
        this.clientTargetRadius = this.radius;
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
        this.ensureFallbackMovementPlan();
        this.advanceAlongMovementPlan(null);
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

    private void updateGroundedVisualBase(ServerLevel level) {
        float groundedBottomY = TornadoManager.resolveGroundedBottomY(level, this.position, this.visualBottomY);
        this.visualBottomY = groundedBottomY;
        this.position = new Vec3(this.position.x, groundedBottomY, this.position.z);

        float cloudBase = CloudManager.get(level).getCloudHeight();
        float reachToCloudBase = Math.max(0.0F, cloudBase - groundedBottomY);
        this.targetVisualHeight = Math.max(96.0F, reachToCloudBase + this.maxRadius * 6.0F + 40.0F);
        this.applyIntensityToVisuals();
    }

    private void updateMovement(ServerLevel level, long gameTime) {
        // Route selection runs on a slower cadence. Per-tick movement only blends toward the
        // current plan so the tornado keeps committing to a path instead of re-steering every tick.
        this.ensureMovementPlan(level, gameTime);
        this.advanceAlongMovementPlan(level);
    }

    private void ensureMovementPlan(ServerLevel level, long gameTime) {
        if (!this.shouldReplanMovement(level)) {
            return;
        }
        this.applyMovementPlan(StormMotionModel.planTornadoRoute(
                level,
                this.id,
                this.position,
                this.wind,
                Math.max(this.normalizedIntensity, 0.08F),
                this.stormLevel,
                this.headingRadians,
                gameTime,
                this.anchorX,
                this.anchorZ
        ));
    }

    private void ensureFallbackMovementPlan() {
        if (!this.shouldReplanMovement(null)) {
            return;
        }
        this.applyMovementPlan(StormMotionModel.planFallbackTornadoRoute(
                this.id,
                this.position,
                this.wind,
                Math.max(this.normalizedIntensity, 0.08F),
                this.stormLevel,
                this.headingRadians,
                this.ageTicks,
                this.anchorX,
                this.anchorZ
        ));
    }

    private boolean shouldReplanMovement(@Nullable ServerLevel level) {
        if (this.routeWaypoint == null || this.routeTicksRemaining <= 0) {
            return true;
        }
        if (this.hasReachedRouteWaypoint()) {
            return true;
        }
        if (level != null && StormShieldManager.isProtected(level, this.routeWaypoint)) {
            return true;
        }
        double dx = this.routeWaypoint.x - this.anchorX;
        double dz = this.routeWaypoint.z - this.anchorZ;
        return dx * dx + dz * dz > MOVEMENT_ROUTE_LEASH_RADIUS * MOVEMENT_ROUTE_LEASH_RADIUS;
    }

    private boolean hasReachedRouteWaypoint() {
        if (this.routeWaypoint == null) {
            return true;
        }
        double dx = this.routeWaypoint.x - this.position.x;
        double dz = this.routeWaypoint.z - this.position.z;
        double dynamicThreshold = Math.max(MOVEMENT_ROUTE_REACHED_DISTANCE_SQR, this.motion.lengthSqr() * 48.0D);
        return dx * dx + dz * dz <= dynamicThreshold;
    }

    private void applyMovementPlan(StormMotionModel.TornadoRoutePlan plan) {
        this.routeWaypoint = new Vec3(plan.waypoint().x, this.visualBottomY, plan.waypoint().z);
        this.targetHeadingRadians = plan.headingRadians();
        this.targetMoveSpeed = plan.speed();
        this.routeTicksRemaining = Math.max(1, plan.durationTicks());
        if (this.motion.lengthSqr() <= 1.0E-5D) {
            this.headingRadians = this.targetHeadingRadians;
            this.plannedMoveSpeed = this.targetMoveSpeed;
        }
    }

    private void advanceAlongMovementPlan(@Nullable ServerLevel level) {
        Vec3 waypoint = this.routeWaypoint != null
                ? this.routeWaypoint
                : this.position.add(horizontalVector(this.targetHeadingRadians).scale(12.0D));
        Vec3 toWaypoint = new Vec3(waypoint.x - this.position.x, 0.0D, waypoint.z - this.position.z);
        float desiredHeading = toWaypoint.lengthSqr() > 1.0E-4D
                ? (float) Math.atan2(toWaypoint.z, toWaypoint.x)
                : this.targetHeadingRadians;
        float stormFactor = StormSeverityScale.toNormalized(this.stormLevel);
        float maxTurn = MOVEMENT_HEADING_BLEND
                + this.normalizedIntensity * 0.012F
                + stormFactor * 0.008F;
        this.headingRadians = rotateTowards(this.headingRadians, desiredHeading, maxTurn);
        this.plannedMoveSpeed = Mth.lerp(MOVEMENT_SPEED_BLEND, this.plannedMoveSpeed, this.targetMoveSpeed);

        Vec3 plannedVector = horizontalVector(this.headingRadians).scale(this.plannedMoveSpeed);
        Vec3 ambientVector = horizontalVector(this.wind.angleRadians()).scale(
                Math.max(0.6F, this.wind.baseSpeed()) * (MOVEMENT_AMBIENT_SCALE + this.normalizedIntensity * 0.0012D)
        );
        Vec3 leashCorrection = this.sampleMovementLeashCorrection();
        Vec3 shieldCorrection = Vec3.ZERO;
        if (level != null) {
            Vec3 avoidance = StormShieldManager.sampleAvoidance(level, this.position, 24.0D + this.stormLevel * 8.0D);
            if (avoidance.lengthSqr() > 1.0E-6D) {
                shieldCorrection = avoidance.scale(0.05D + stormFactor * 0.08D);
                if (this.routeTicksRemaining > MOVEMENT_REPLAN_ON_AVOIDANCE_TICKS) {
                    this.routeTicksRemaining = MOVEMENT_REPLAN_ON_AVOIDANCE_TICKS;
                }
            }
        }

        Vec3 targetMotion = plannedVector.add(ambientVector).add(leashCorrection).add(shieldCorrection);
        this.motion = this.motion.lerp(targetMotion, MOVEMENT_VECTOR_BLEND + this.normalizedIntensity * 0.05D);
        if (this.motion.lengthSqr() <= 1.0E-6D && targetMotion.lengthSqr() > 0.0D) {
            this.motion = targetMotion;
        }

        double maxSpeed = Math.max(0.05D, this.targetMoveSpeed * 1.75D + 0.04D);
        if (this.motion.lengthSqr() > maxSpeed * maxSpeed) {
            this.motion = this.motion.normalize().scale(maxSpeed);
        }

        this.position = new Vec3(this.position.x + this.motion.x, this.visualBottomY, this.position.z + this.motion.z);
        if (this.routeTicksRemaining > 0) {
            this.routeTicksRemaining--;
        }
    }

    private Vec3 sampleMovementLeashCorrection() {
        double dx = this.anchorX - this.position.x;
        double dz = this.anchorZ - this.position.z;
        double distSqr = dx * dx + dz * dz;
        if (distSqr <= MOVEMENT_ROUTE_LEASH_RADIUS * MOVEMENT_ROUTE_LEASH_RADIUS) {
            return Vec3.ZERO;
        }
        double dist = Math.sqrt(distSqr);
        double strength = Mth.clamp(
                (dist - MOVEMENT_ROUTE_LEASH_RADIUS) / (MOVEMENT_ROUTE_LEASH_RADIUS * 0.80D),
                0.0D,
                1.0D
        );
        return new Vec3(dx / Math.max(dist, 0.001D), 0.0D, dz / Math.max(dist, 0.001D))
                .scale(0.03D + strength * 0.11D);
    }

    private static Vec3 horizontalVector(float heading) {
        return new Vec3(Math.cos(heading), 0.0D, Math.sin(heading));
    }

    private static float rotateTowards(float current, float target, float maxTurn) {
        float delta = Mth.wrapDegrees((float) Math.toDegrees(target - current));
        float clamped = Mth.clamp(delta, (float) Math.toDegrees(-maxTurn), (float) Math.toDegrees(maxTurn));
        return current + (float) Math.toRadians(clamped);
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
        double influence = this.getOuterInfluenceRadius();
        double minY = anchor.y + ENTITY_MIN_VERTICAL_RANGE;
        double maxY = this.getInteractionTopY(anchor) + ENTITY_MAX_VERTICAL_PADDING;
        AABB box = new AABB(
                anchor.x - influence, minY,
                anchor.z - influence, anchor.x + influence,
                maxY, anchor.z + influence
        );

        int eligibleEntities = 0;
        for (Entity entity : serverLevel.getEntities(null, box)) {
            if (!this.isAffectedEntity(serverLevel, entity)) {
                continue;
            }
            eligibleEntities++;
            this.applyTornadoForces(serverLevel, entity, anchor);
        }
        this.debugEligibleEntityCount = eligibleEntities;
        this.debugCapturedEntityCount = this.capturedEntities.size();
    }

    private boolean demolishBlocks(ServerLevel level) {
        if (!AtmoCommonConfig.ENABLE_TORNADO_DESTRUCTION.get()) {
            return false;
        }

        Vec3 anchor = this.getInteractionAnchor(level);
        BlockPos center = BlockPos.containing(anchor);
        float stormFactor = StormSeverityScale.toNormalized(this.stormLevel);
        float windfieldWidth = this.getWindfieldWidth();
        float destructionRadius = (float) Math.max(this.getCaptureRadius() * 0.88D, this.radius * (2.35F + stormFactor * 0.60F));
        float coreRadius = (float) Math.max(this.getCoreRadius(), this.radius * (1.10F + stormFactor * 0.18F));
        this.debugDestructionSweepRadius = destructionRadius;
        int intRadius = Mth.ceil(destructionRadius);
        double outerSq = destructionRadius * destructionRadius;
        RandomSource random = RandomSource.create(this.id.getLeastSignificantBits() ^ (this.ageTicks * 31L));

        int scannedColumns = 0;
        int eligibleColumns = 0;
        int candidateBlocks = 0;
        int leafLogDestroyed = 0;
        int weakDestroyed = 0;
        int grassScoured = 0;
        int glassDestroyed = 0;
        int[] spawnedDebrisEntities = new int[] {0};
        int maxLeafLogBreaks = 280 + Mth.floor(this.normalizedIntensity * 420.0F + stormFactor * 320.0F);
        int maxWeakBreaks = 220 + Mth.floor(this.normalizedIntensity * 280.0F + stormFactor * 220.0F);
        int maxGrassScours = 96 + Mth.floor(this.normalizedIntensity * 132.0F + stormFactor * 84.0F);
        int maxGlassBreaks = 36 + Mth.floor(this.normalizedIntensity * 64.0F + stormFactor * 44.0F);
        int maxDebrisEntitySpawns = BASE_MAX_DEBRIS_ENTITY_SPAWNS
                + Mth.floor(this.normalizedIntensity * 10.0F + stormFactor * 8.0F);
        int minBuildY = level.getMinBuildHeight();
        int maxBuildY = level.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -intRadius; dx <= intRadius; dx++) {
            for (int dz = -intRadius; dz <= intRadius; dz++) {
                double horizontalDistSq = dx * dx + dz * dz;
                if (horizontalDistSq > outerSq) {
                    continue;
                }

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                cursor.set(x, Mth.floor(anchor.y), z);
                if (!level.isLoaded(cursor)) {
                    continue;
                }
                scannedColumns++;
                float distance = Mth.sqrt((float) horizontalDistSq);
                float columnStrength = 1.0F - smoothStep(coreRadius, destructionRadius, distance);
                boolean coreColumn = distance <= coreRadius;
                if (columnStrength <= 0.06F && !coreColumn) {
                    continue;
                }
                eligibleColumns++;

                int terrainY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                int canopyY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                boolean forceBreak = coreColumn || columnStrength >= 0.72F;
                int sweepStartY = Math.max(minBuildY, terrainY);
                int sweepEndY = Math.min(maxBuildY, Math.max(canopyY + 8, terrainY + 12 + Mth.floor(columnStrength * 14.0F)));
                for (int y = sweepStartY; y <= sweepEndY; y++) {
                    if (weakDestroyed >= maxWeakBreaks && grassScoured >= maxGrassScours && glassDestroyed >= maxGlassBreaks) {
                        break;
                    }

                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor) || StormShieldManager.isProtected(level, cursor)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    candidateBlocks++;

                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                        if (leafLogDestroyed < maxLeafLogBreaks) {
                            float breakChance = Mth.clamp(
                                    0.44F + columnStrength * 0.46F + this.normalizedIntensity * 0.24F + stormFactor * 0.16F,
                                    0.0F,
                                    1.0F
                            );
                            leafLogDestroyed += this.destroyTreeClusterImmediate(
                                    level,
                                    cursor.immutable(),
                                    random,
                                    breakChance,
                                    forceBreak,
                                    maxLeafLogBreaks - leafLogDestroyed,
                                    anchor,
                                    spawnedDebrisEntities,
                                    maxDebrisEntitySpawns
                            );
                        }
                        continue;
                    }

                    if (AtmosphereUtils.isGlass(state)) {
                        if (glassDestroyed >= maxGlassBreaks) {
                            continue;
                        }
                        float glassChance = Mth.clamp(
                                0.24F + columnStrength * 0.56F + this.normalizedIntensity * 0.22F + stormFactor * 0.18F,
                                0.0F,
                                1.0F
                        );
                        if (forceBreak || random.nextFloat() < glassChance) {
                            if (this.removeBlockWithDebris(
                                    level,
                                    cursor.immutable(),
                                    state,
                                    anchor,
                                    random,
                                    spawnedDebrisEntities,
                                    maxDebrisEntitySpawns,
                                    0.10F
                            )) {
                                glassDestroyed++;
                            }
                        }
                        continue;
                    }

                    if (isSurfaceSoilBlock(state)) {
                        if (grassScoured >= maxGrassScours) {
                            continue;
                        }
                        float scourChance = Mth.clamp(
                                0.26F + columnStrength * 0.44F + this.normalizedIntensity * 0.14F + stormFactor * 0.10F,
                                0.0F,
                                1.0F
                        );
                        boolean extremeExcavation = forceBreak
                                && columnStrength >= 0.96F
                                && this.normalizedIntensity >= 0.96F
                                && stormFactor >= 0.85F
                                && random.nextFloat() < (state.is(Blocks.DIRT) ? 0.025F : 0.055F);
                        if (extremeExcavation) {
                            if (this.removeBlockWithDebris(
                                    level,
                                    cursor.immutable(),
                                    state,
                                    anchor,
                                    random,
                                    spawnedDebrisEntities,
                                    maxDebrisEntitySpawns,
                                    0.28F
                            )) {
                                weakDestroyed++;
                            }
                        } else if (!state.is(Blocks.DIRT) && (forceBreak || random.nextFloat() < scourChance)) {
                            level.setBlockAndUpdate(cursor, Blocks.DIRT.defaultBlockState());
                            grassScoured++;
                        }
                        continue;
                    }

                    boolean looseTerrain = isLooseTerrainBlock(state);
                    boolean weakBlock = isWeakBlock(state, level, cursor, stormFactor);
                    boolean eligibleWeak = isVegetationBlock(state) || isSimpleStructureBlock(state) || looseTerrain || weakBlock;
                    if (!eligibleWeak || weakDestroyed >= maxWeakBreaks) {
                        continue;
                    }

                    float breakChance = Mth.clamp(
                            0.20F + columnStrength * 0.54F + this.normalizedIntensity * 0.24F + stormFactor * 0.20F,
                            0.0F,
                            1.0F
                    );
                    if (looseTerrain) {
                        breakChance *= 1.25F;
                    }
                    if (isVegetationBlock(state)) {
                        breakChance *= 1.18F;
                    }
                    if (forceBreak || random.nextFloat() < breakChance) {
                        if (this.removeBlockWithDebris(
                                level,
                                cursor.immutable(),
                                state,
                                anchor,
                                random,
                                spawnedDebrisEntities,
                                maxDebrisEntitySpawns,
                                this.getDebrisSpawnChance(state)
                        )) {
                            weakDestroyed++;
                        }
                    }
                }
            }
        }

        this.debugDestructionCandidateBlockCount = candidateBlocks;
        this.debugDestroyedLeafLogCount = leafLogDestroyed;
        this.debugDestroyedWeakCount = weakDestroyed;
        this.debugDestroyedGrassCount = grassScoured;
        this.debugDestroyedGlassCount = glassDestroyed;
        this.debugDestroyedBlockCount = leafLogDestroyed + weakDestroyed + grassScoured + glassDestroyed;
        float debrisGain = Math.min(1.0F,
                leafLogDestroyed * 0.010F
                        + weakDestroyed * 0.018F
                        + grassScoured * 0.008F
                        + glassDestroyed * 0.024F);
        this.recentDebrisScore = Mth.clamp(this.recentDebrisScore + debrisGain, 0.0F, 1.0F);

        return leafLogDestroyed > 0 || weakDestroyed > 0 || grassScoured > 0 || glassDestroyed > 0;
    }

    private int destroyTreeClusterImmediate(ServerLevel level,
                                            BlockPos origin,
                                            RandomSource random,
                                            float breakChance,
                                            boolean forceBreak,
                                            int remainingBudget,
                                            Vec3 anchor,
                                            int[] spawnedDebrisEntities,
                                            int maxDebrisEntitySpawns) {
        if (remainingBudget <= 0) {
            return 0;
        }

        int originX = origin.getX();
        int originY = origin.getY();
        int originZ = origin.getZ();
        int destroyed = 0;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin.asLong());

        while (!queue.isEmpty() && destroyed < remainingBudget && visited.size() <= TREE_CLUSTER_VISIT_LIMIT) {
            BlockPos current = queue.removeFirst();
            if (!level.isLoaded(current) || StormShieldManager.isProtected(level, current)) {
                continue;
            }

            BlockState state = level.getBlockState(current);
            if (!(state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || isVegetationBlock(state))) {
                continue;
            }

            if (forceBreak || random.nextFloat() < breakChance) {
                if (this.removeBlockWithDebris(
                        level,
                        current,
                        state,
                        anchor,
                        random,
                        spawnedDebrisEntities,
                        maxDebrisEntitySpawns,
                        this.getDebrisSpawnChance(state)
                )) {
                    destroyed++;
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        int localX = next.getX() - originX;
                        int localY = next.getY() - originY;
                        int localZ = next.getZ() - originZ;
                        if (Math.abs(localX) > TREE_CLUSTER_HORIZONTAL_RADIUS
                                || Math.abs(localZ) > TREE_CLUSTER_HORIZONTAL_RADIUS
                                || localY < -TREE_CLUSTER_BELOW
                                || localY > TREE_CLUSTER_ABOVE) {
                            continue;
                        }
                        if (visited.add(next.asLong())) {
                            queue.addLast(next);
                        }
                    }
                }
            }
        }
        return destroyed;
    }

    private boolean removeBlockWithDebris(ServerLevel level,
                                          BlockPos pos,
                                          BlockState state,
                                          Vec3 anchor,
                                          RandomSource random,
                                          int[] spawnedDebrisEntities,
                                          int maxDebrisEntitySpawns,
                                          float debrisChance) {
        float clampedDebrisChance = Mth.clamp(debrisChance, 0.0F, MAX_DEBRIS_ENTITY_SPAWN_CHANCE);
        if (spawnedDebrisEntities[0] < maxDebrisEntitySpawns
                && this.isVisualDebrisBlock(state)
                && random.nextFloat() < clampedDebrisChance
                && this.spawnVisualDebrisEntity(level, pos, state, anchor, random)) {
            spawnedDebrisEntities[0]++;
            return true;
        }
        return level.destroyBlock(pos, false);
    }

    private boolean spawnVisualDebrisEntity(ServerLevel level,
                                            BlockPos pos,
                                            BlockState state,
                                            Vec3 anchor,
                                            RandomSource random) {
        if (!level.isLoaded(pos) || StormShieldManager.isProtected(level, pos) || state.isAir()) {
            return false;
        }

        FallingBlockEntity debris = FallingBlockEntity.fall(level, pos, state);
        debris.disableDrop();
        debris.setNoGravity(true);
        debris.time = 560 + random.nextInt(20);

        Vec3 blockCenter = Vec3.atCenterOf(pos);
        Vec3 towardAnchor = anchor.subtract(blockCenter);
        double horizontalDistance = Math.max(Math.hypot(towardAnchor.x, towardAnchor.z), 0.001D);
        Vec3 inward = new Vec3(towardAnchor.x / horizontalDistance, 0.0D, towardAnchor.z / horizontalDistance);
        float rotationDirection = random.nextBoolean() ? 1.0F : -1.0F;
        Vec3 tangential = new Vec3(-inward.z * rotationDirection, 0.0D, inward.x * rotationDirection);
        double inwardStrength = 0.08D + this.normalizedIntensity * 0.12D;
        double tangentialStrength = 0.12D + StormSeverityScale.toNormalized(this.stormLevel) * 0.10D;
        double verticalStrength = 0.18D + this.normalizedIntensity * 0.12D + random.nextDouble() * 0.06D;
        debris.setDeltaMovement(
                inward.scale(inwardStrength)
                        .add(tangential.scale(tangentialStrength))
                        .add(0.0D, verticalStrength, 0.0D)
        );
        return true;
    }

    private float getDebrisSpawnChance(BlockState state) {
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
            return 0.38F;
        }
        if (state.is(BlockTags.LEAVES)) {
            return 0.24F;
        }
        if (isSurfaceSoilBlock(state) || isLooseTerrainBlock(state)) {
            return 0.30F;
        }
        if (isSimpleStructureBlock(state)) {
            return 0.32F;
        }
        return 0.16F;
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
                || state.is(BlockTags.SAPLINGS)
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

    private static boolean isSurfaceSoilBlock(BlockState state) {
        return state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.MUD)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.MOSS_BLOCK);
    }

    private static boolean isLooseTerrainBlock(BlockState state) {
        return state.is(Blocks.CLAY)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW);
    }

    private boolean isVisualDebrisBlock(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.FENCES)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.WOOL)
                || isSurfaceSoilBlock(state)
                || isLooseTerrainBlock(state);
    }

    private static boolean isWeakBlock(BlockState state, Level level, BlockPos pos, float stormFactor) {
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.LOGS)
                || AtmosphereUtils.isGlass(state)) {
            return false;
        }
        float destroySpeed = state.getDestroySpeed(level, pos);
        if (destroySpeed < 0.0F) {
            return false;
        }
        float threshold = 1.1F + stormFactor * 1.8F;
        return destroySpeed <= threshold;
    }

    private float getWindfieldWidth() {
        return Math.max(this.radius * (1.65F + StormSeverityScale.toNormalized(this.stormLevel) * 0.45F), 28.0F);
    }

    private double getCoreRadius() {
        return Math.max(this.getWindfieldWidth() * CORE_RADIUS_FACTOR, this.radius * 1.05F);
    }

    private double getCaptureRadius() {
        return Math.max(this.getWindfieldWidth() * CAPTURE_RADIUS_FACTOR, this.getCoreRadius() + 8.0D);
    }

    private double getOuterInfluenceRadius() {
        return Math.max(this.getWindfieldWidth() * 1.75D, this.getCaptureRadius() + OUTER_ENTITY_INFLUENCE_PADDING);
    }

    private double getInteractionTopY(Vec3 anchor) {
        return anchor.y + Math.max(36.0D, this.visualHeight * 0.96D);
    }

    private boolean isAffectedEntity(ServerLevel level, Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.noPhysics) {
            return false;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return !StormShieldManager.isProtected(level, entity.position());
    }

    private void applyTornadoForces(ServerLevel level, Entity entity, Vec3 anchor) {
        CapturedEntityState captured = this.capturedEntities.get(entity.getId());
        double outerInfluenceRadius = captured != null
                ? this.getOuterInfluenceRadius() * CAPTURE_HYSTERESIS_FACTOR
                : this.getOuterInfluenceRadius();
        double captureRadius = this.getCaptureRadius();
        double coreRadius = this.getCoreRadius();
        double topY = this.getInteractionTopY(anchor);
        int terrainY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                entity.blockPosition().getX(),
                entity.blockPosition().getZ()
        ) - 1;

        if (entity.getY() + entity.getBbHeight() < terrainY - 1.5D
                || entity.getY() > topY + ENTITY_MAX_VERTICAL_PADDING) {
            this.capturedEntities.remove(entity.getId());
            return;
        }

        Vec3 relativePos = entity.position().subtract(anchor);
        double horizontalDistance = Math.hypot(relativePos.x, relativePos.z);
        if (horizontalDistance > outerInfluenceRadius) {
            this.capturedEntities.remove(entity.getId());
            return;
        }

        double safeDistance = Math.max(horizontalDistance, 0.001D);
        Vec3 inward = new Vec3(-relativePos.x / safeDistance, 0.0D, -relativePos.z / safeDistance);
        float rotationDirection = captured != null ? captured.rotationDirection : this.getRotationDirection(entity);
        Vec3 tangential = new Vec3(-inward.z * rotationDirection, 0.0D, inward.x * rotationDirection);
        float stormFactor = StormSeverityScale.toNormalized(this.stormLevel);
        float tornadoForce = Mth.clamp(this.normalizedIntensity * 0.72F + stormFactor * 0.28F, 0.25F, 1.0F);
        float approachFactor = 1.0F - smoothStep((float) captureRadius, (float) outerInfluenceRadius, (float) horizontalDistance);
        float coreFactor = 1.0F - smoothStep((float) (coreRadius * 0.72D), (float) (captureRadius * 0.94D), (float) horizontalDistance);
        float heightNorm = Mth.clamp((float) ((entity.getY() - anchor.y) / Math.max(topY - anchor.y, 1.0D)), 0.0F, 1.25F);
        float liftWindow = 1.0F - smoothStep(0.88F, 1.05F, heightNorm);
        boolean shouldCapture = captured != null
                || horizontalDistance <= captureRadius
                || (approachFactor > 0.72F && heightNorm < 1.0F);
        if (captured == null && shouldCapture) {
            captured = this.createCaptureState(entity, captureRadius, horizontalDistance);
            this.capturedEntities.put(entity.getId(), captured);
        }

        Vec3 translationAssist = this.motion.scale(0.10D + tornadoForce * 0.10D);
        Vec3 add;
        if (captured != null) {
            captured.lastSeenAge = this.ageTicks;
            captured.captureTicks++;
            float captureProgress = smoothStep(2.0F, CAPTURE_FULL_TICKS, captured.captureTicks);
            float ascentProgress = smoothStep(10.0F, CAPTURE_ASCENT_TICKS, captured.captureTicks);
            float desiredBand = Mth.lerp(ascentProgress, OUTER_ORBIT_RADIUS_FACTOR, INNER_ORBIT_RADIUS_FACTOR + coreFactor * 0.06F);
            captured.orbitRadiusFactor = Mth.lerp(0.20F, captured.orbitRadiusFactor, desiredBand);
            captured.orbitAngle += (0.16F + tornadoForce * 0.14F + captureProgress * 0.12F + coreFactor * 0.06F)
                    * captured.rotationDirection;

            double desiredRadius = Math.max(coreRadius * 0.72D, captureRadius * captured.orbitRadiusFactor);
            double liftStep = (CAPTURED_LIFT_FORCE + tornadoForce * 0.26F + ascentProgress * 0.42F + coreFactor * 0.18F)
                    * captured.liftBias
                    * liftWindow;
            double desiredHeight = Mth.clamp(
                    entity.getY() + liftStep,
                    anchor.y + 1.5D,
                    topY + ENTITY_RELEASE_HEIGHT_PADDING
            );
            Vec3 orbitTarget = new Vec3(
                    Math.cos(captured.orbitAngle) * desiredRadius,
                    desiredHeight,
                    Math.sin(captured.orbitAngle) * desiredRadius
            ).add(anchor.x, 0.0D, anchor.z);
            Vec3 towardOrbit = orbitTarget.subtract(entity.position());
            Vec3 horizontalOrbitOffset = new Vec3(towardOrbit.x, 0.0D, towardOrbit.z);
            Vec3 orbitCorrection = horizontalOrbitOffset.lengthSqr() > 1.0E-4
                    ? horizontalOrbitOffset.normalize()
                    : Vec3.ZERO;

            add = orbitCorrection.scale(CAPTURED_SUCTION_FORCE + tornadoForce * 0.24F + captureProgress * 0.18F)
                    .add(tangential.scale(CAPTURED_TANGENTIAL_FORCE + tornadoForce * 0.20F + ascentProgress * 0.10F))
                    .add(0.0D, liftStep, 0.0D)
                    .add(translationAssist);
            if (horizontalDistance < coreRadius * 0.42D) {
                add = add.add(inward.scale(-0.10D - coreFactor * 0.10D));
            }
        } else {
            float suctionStrength = BASE_SUCTION_FORCE + approachFactor * (0.28F + tornadoForce * 0.18F);
            float tangentialStrength = BASE_TANGENTIAL_FORCE + approachFactor * 0.12F + coreFactor * 0.06F;
            float liftStrength = (BASE_LIFT_FORCE + approachFactor * 0.16F + coreFactor * 0.12F)
                    * (0.75F + tornadoForce * 0.45F)
                    * liftWindow;

            add = inward.scale(suctionStrength)
                    .add(tangential.scale(tangentialStrength))
                    .add(0.0D, liftStrength, 0.0D)
                    .add(translationAssist.scale(0.60D));
        }

        if (entity.onGround()) {
            add = add.add(0.0D, captured != null ? 0.35D : 0.18D, 0.0D);
        }

        this.recordForceSample(
                Math.max(0.0D, add.x * inward.x + add.z * inward.z),
                Math.max(0.0D, add.y)
        );

        Vec3 current = entity.getDeltaMovement();
        Vec3 damped = captured != null
                ? new Vec3(current.x * 0.54D, Math.max(current.y * 0.55D, 0.0D), current.z * 0.54D)
                : new Vec3(current.x * 0.82D, Math.max(current.y * 0.35D, -0.02D), current.z * 0.82D);
        entity.setDeltaMovement(damped.add(add));

        if (captured != null && (entity.getY() > topY + ENTITY_RELEASE_HEIGHT_PADDING || captured.captureTicks > CAPTURE_RELEASE_TICKS)) {
            this.capturedEntities.remove(entity.getId());
            entity.setDeltaMovement(entity.getDeltaMovement().add(tangential.scale(0.18D + tornadoForce * 0.08D)).add(0.0D, 0.20D, 0.0D));
        }
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        entity.fallDistance = 0.0F;
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
        this.applyEntityDamage(entity, captured, approachFactor, coreFactor, liftWindow);
    }

    private CapturedEntityState createCaptureState(Entity entity, double captureRadius, double dist) {
        double angle = Math.atan2(entity.getZ() - this.position.z, entity.getX() - this.position.x);
        float orbitRadiusFactor = Mth.clamp((float) (dist / Math.max(captureRadius, 0.001D)), 0.42F, 0.88F);
        float liftBias = 0.88F + (float) StormMotionModel.noise01(this.id, entity.getId() * 31L, 0.07F) * 0.28F;
        return new CapturedEntityState(
                angle,
                orbitRadiusFactor,
                this.getRotationDirection(entity),
                liftBias,
                this.ageTicks,
                0
        );
    }

    private void applyEntityDamage(Entity entity,
                                   @Nullable CapturedEntityState captured,
                                   float approachFactor,
                                   float coreFactor,
                                   float liftWindow) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        if (this.ageTicks % ENTITY_DAMAGE_INTERVAL_TICKS != Math.floorMod(entity.getId(), ENTITY_DAMAGE_INTERVAL_TICKS)) {
            return;
        }
        if (captured == null && coreFactor < 0.28F && approachFactor < 0.48F) {
            return;
        }

        float stormFactor = StormSeverityScale.toNormalized(this.stormLevel);
        float intensityFactor = 0.48F + this.normalizedIntensity * 0.96F + stormFactor * 0.42F;
        float pressureFactor = captured != null ? 1.35F : 0.82F;
        float zoneFactor = 0.34F + coreFactor * 0.92F + approachFactor * 0.38F + (1.0F - liftWindow) * 0.12F;
        float damage = (float) (this.getDamageMultiplier() * 0.08D) * intensityFactor * pressureFactor * zoneFactor;
        damage = Mth.clamp(damage, captured != null ? 1.5F : 0.75F, 9.0F);
        if (damage > 0.0F) {
            living.hurt(living.damageSources().generic(), damage);
        }
    }

    private float getRotationDirection(Entity entity) {
        return (((this.id.getLeastSignificantBits() ^ entity.getId()) & 1L) == 0L) ? 1.0F : -1.0F;
    }

    private void resetRuntimeDebugStats() {
        this.debugEligibleEntityCount = 0;
        this.debugCapturedEntityCount = this.capturedEntities.size();
        this.debugForceSampleCount = 0;
        this.debugPullForceSum = 0.0D;
        this.debugUpwardForceSum = 0.0D;
        this.debugPullForceMax = 0.0D;
        this.debugUpwardForceMax = 0.0D;
        this.debugDestructionSweepRadius = 0.0F;
        this.debugDestructionCandidateBlockCount = 0;
        this.debugDestroyedBlockCount = 0;
        this.debugDestroyedLeafLogCount = 0;
        this.debugDestroyedWeakCount = 0;
        this.debugDestroyedGrassCount = 0;
        this.debugDestroyedGlassCount = 0;
    }

    private void recordForceSample(double pullForce, double upwardForce) {
        this.debugForceSampleCount++;
        this.debugPullForceSum += pullForce;
        this.debugUpwardForceSum += upwardForce;
        this.debugPullForceMax = Math.max(this.debugPullForceMax, pullForce);
        this.debugUpwardForceMax = Math.max(this.debugUpwardForceMax, upwardForce);
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
        this.targetIntensity = Math.max(0.18F, this.targetIntensity * (1.0F - penalty * 0.18F));
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
        private float orbitRadiusFactor;
        private final float rotationDirection;
        private final float liftBias;
        private int lastSeenAge;
        private int captureTicks;

        private CapturedEntityState(double orbitAngle,
                                    float orbitRadiusFactor,
                                    float rotationDirection,
                                    float liftBias,
                                    int lastSeenAge,
                                    int captureTicks) {
            this.orbitAngle = orbitAngle;
            this.orbitRadiusFactor = orbitRadiusFactor;
            this.rotationDirection = rotationDirection;
            this.liftBias = liftBias;
            this.lastSeenAge = lastSeenAge;
            this.captureTicks = captureTicks;
        }
    }

    public record RuntimeDebugSnapshot(
            UUID id,
            StormLifecyclePhase phase,
            float normalizedIntensity,
            int eligibleEntityCount,
            int capturedEntityCount,
            double averagePullForce,
            double maxPullForce,
            double averageUpwardForce,
            double maxUpwardForce,
            float destructionSweepRadius,
            int destructionCandidateBlockCount,
            int destroyedBlockCount,
            int destroyedLeafLogCount,
            int destroyedWeakCount,
            int destroyedGrassCount,
            int destroyedGlassCount
    ) {
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}

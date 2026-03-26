package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.Gabou.projectatmosphere.modules.weather.StormMotionModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class HurricaneInstance {
    private static final int AMBIENT_WIND_INTERVAL_TICKS = 20;

    private final UUID id;
    public Vec3 position;
    public float radius;
    public WindVector wind;
    public final HurricaneCategory category;
    private final float maxRadius;
    private final float targetIntensity;
    private float eyewallRadius;
    private float normalizedIntensity;
    private StormLifecyclePhase phase;
    private Vec3 motion = Vec3.ZERO;
    private int ageTicks;
    private int phaseTicks;
    private int formationTicks;
    private int activeTicks;
    private int dissipationTicks;
    private long lastAmbientWindTick = Long.MIN_VALUE;

    public HurricaneInstance(Vec3 position, float radius, WindVector wind, HurricaneCategory category) {
        this(UUID.randomUUID(), position, radius, wind, category);
    }

    public HurricaneInstance(UUID id, Vec3 position, float radius, WindVector wind, HurricaneCategory category) {
        this.id = id;
        this.position = position;
        this.radius = radius;
        this.maxRadius = radius;
        this.wind = wind;
        this.category = category;
        this.targetIntensity = switch (category) {
            case ONE -> 0.30F;
            case TWO -> 0.42F;
            case THREE -> 0.58F;
            case FOUR -> 0.78F;
            case FIVE -> 1.0F;
        };
        this.normalizedIntensity = Math.max(0.18F, this.targetIntensity * 0.45F);
        this.phase = StormLifecyclePhase.FORMING;
        this.eyewallRadius = radius * 0.45F;
        this.formationTicks = 600;
        this.activeTicks = 9600 + Math.round(this.targetIntensity * 7200.0F);
        this.dissipationTicks = 1800;
        this.applyIntensityToVisuals();
    }

    public UUID getId() {
        return this.id;
    }

    public float getLifetimeSeconds() {
        return this.ageTicks / 20.0F;
    }

    public float getNormalizedIntensity() {
        return this.normalizedIntensity;
    }

    public StormLifecyclePhase getPhase() {
        return this.phase;
    }

    public float getEyewallRadius() {
        return this.eyewallRadius;
    }

    public boolean isDead() {
        return this.phase.isTerminal();
    }

    public void markDissipating() {
        if (this.phase == StormLifecyclePhase.DISSIPATED || this.phase == StormLifecyclePhase.DISSIPATING) {
            return;
        }
        this.phase = StormLifecyclePhase.DISSIPATING;
        this.phaseTicks = 0;
    }

    public void tickServer(ServerLevel level, long gameTime) {
        this.ageTicks++;
        this.phaseTicks++;
        this.wind = ForecastOrchestrator.getWind(level, BlockPos.containing(this.position), gameTime);
        this.tickLifecycle();
        this.updateMovement(gameTime);

        if (gameTime - this.lastAmbientWindTick >= AMBIENT_WIND_INTERVAL_TICKS) {
            this.lastAmbientWindTick = gameTime;
            this.applyAmbientWind(level);
        }
    }

    public HurricaneSnapshot snapshot() {
        return new HurricaneSnapshot(
                this.id,
                this.position,
                this.radius,
                this.eyewallRadius,
                this.wind.baseSpeed(),
                this.wind.angleRadians(),
                this.wind.gustSpeed(),
                this.normalizedIntensity,
                this.category,
                this.phase
        );
    }

    public void applySnapshot(HurricaneSnapshot snapshot) {
        this.position = snapshot.position();
        this.radius = snapshot.radius();
        this.eyewallRadius = snapshot.eyewallRadius();
        this.wind = new WindVector(snapshot.windSpeed(), snapshot.windAngle(), snapshot.windGust());
        this.normalizedIntensity = snapshot.normalizedIntensity();
        this.phase = snapshot.phase();
    }

    private void tickLifecycle() {
        switch (this.phase) {
            case FORMING -> {
                this.normalizedIntensity = Math.min(this.targetIntensity, this.normalizedIntensity + 1.0F / Math.max(1, this.formationTicks));
                if (this.phaseTicks >= this.formationTicks || this.normalizedIntensity >= this.targetIntensity - 0.02F) {
                    this.phase = StormLifecyclePhase.ACTIVE;
                    this.phaseTicks = 0;
                }
            }
            case ACTIVE -> {
                if (this.phaseTicks >= this.activeTicks) {
                    this.phase = StormLifecyclePhase.DISSIPATING;
                    this.phaseTicks = 0;
                }
            }
            case DISSIPATING -> {
                this.normalizedIntensity = Math.max(0.0F, this.normalizedIntensity - 1.0F / Math.max(1, this.dissipationTicks));
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
        this.radius = this.maxRadius * (0.6F + this.normalizedIntensity * 0.7F);
        this.eyewallRadius = this.radius * (0.22F + this.normalizedIntensity * 0.20F);
    }

    private void updateMovement(long gameTime) {
        Vec3 updated = StormMotionModel.advanceHurricane(
                this.id,
                this.position,
                this.motion,
                this.wind,
                Math.max(this.normalizedIntensity, 0.15F),
                gameTime
        );
        this.motion = updated.subtract(this.position);
        this.position = updated;
    }

    private void applyAmbientWind(ServerLevel level) {
        double influence = this.radius * 1.35F;
        AABB box = new AABB(
                this.position.x - influence, this.position.y - 5.0D,
                this.position.z - influence, this.position.x + influence,
                this.position.y + 70.0D, this.position.z + influence
        );

        double windSpeed = Math.max(this.wind.baseSpeed(), this.wind.gustSpeed()) * (0.01D + this.normalizedIntensity * 0.01D);
        double vx = Math.cos(this.wind.angleRadians()) * windSpeed;
        double vz = Math.sin(this.wind.angleRadians()) * windSpeed;

        for (Entity entity : level.getEntities(null, box)) {
            entity.push(vx, 0.01D * this.normalizedIntensity, vz);
        }
    }
}

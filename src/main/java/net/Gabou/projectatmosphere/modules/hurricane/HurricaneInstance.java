package net.Gabou.projectatmosphere.modules.hurricane;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneSnapshot;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class HurricaneInstance {
    public static final ResourceLocation HURRICANE_CLOUD_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("projectatmosphere", "hurricane");

    private static final float DEFAULT_ANCHOR_Y = 180.0F;
    private static final float MIN_WORLD_ANCHOR_Y = 128.0F;
    private static final float CLOUD_LAYER_DESCENT = 620.0F;
    private static final float GROUND_CLEARANCE = 48.0F;
    private static final float OUTER_SHELL_SCALE = 0.25F;
    private static final int WIND_FIELD_INTERVAL_TICKS = 2;
    private static final int DESTRUCTION_INTERVAL_TICKS = 8;

    public final UUID id;
    @Nullable
    private final UUID cycloneId;
    private final boolean debugSpawn;

    public Vec3 position;
    public float radius;
    public WindVector wind;
    public HurricaneCategory category;

    private float cycloneRadius;
    private float cycloneIntensity;
    private float destructiveStrength;
    private float targetDestructiveStrength;
    private float anchorY = DEFAULT_ANCHOR_Y;
    private int ageTicks;
    private int phaseTicks;
    private int formationTicks;
    private int activeTicks;
    private int dissipationTicks;
    private long lastWindFieldTick = Long.MIN_VALUE;
    private long lastDestructionTick = Long.MIN_VALUE;
    private StormLifecyclePhase phase;

    private HurricaneInstance(UUID id, @Nullable UUID cycloneId, Vec3 position, float radius, WindVector wind,
                              HurricaneCategory category, boolean debugSpawn) {
        this.id = id;
        this.cycloneId = cycloneId;
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.category = category;
        this.debugSpawn = debugSpawn;
        this.cycloneRadius = Math.max(radius * 6.0F, 260.0F);
        this.cycloneIntensity = 0.55F;
        this.targetDestructiveStrength = 0.55F;
        this.destructiveStrength = 0.18F;
        this.phase = StormLifecyclePhase.FORMING;
        this.rebuildLifecycleTimings();
    }

    public static HurricaneInstance createDebug(Vec3 position, float radius, WindVector wind, HurricaneCategory category) {
        return new HurricaneInstance(UUID.randomUUID(), null, position, radius, wind, category, true);
    }

    public static HurricaneInstance fromCyclone(ServerLevel level, CycloneSnapshot snapshot, WindVector wind,
                                                HurricaneCategory category, float intensificationStrength) {
        Vec3 center = new Vec3(snapshot.centerX(), level.getSeaLevel(), snapshot.centerZ());
        float localRadius = Mth.clamp(snapshot.radius() * 0.18F, 38.0F, 64.0F);
        HurricaneInstance hurricane = new HurricaneInstance(snapshot.id(), snapshot.id(), center, localRadius, wind, category, false);
        hurricane.updateFromCyclone(level, snapshot, wind, category, intensificationStrength);
        return hurricane;
    }

    public void refreshAnchorY(Level level) {
        CloudManager<?> manager = CloudManager.get(level);
        if (manager == null) {
            this.anchorY = Math.max(this.anchorY, MIN_WORLD_ANCHOR_Y);
            return;
        }

        float cloudHeight = manager.getCloudHeight();
        float minAnchor = level.getSeaLevel() + GROUND_CLEARANCE;
        float maxAnchor = Math.max(minAnchor, cloudHeight - 180.0F);
        this.anchorY = Mth.clamp(cloudHeight - CLOUD_LAYER_DESCENT, minAnchor, maxAnchor);
    }

    public void updateFromCyclone(ServerLevel level, CycloneSnapshot snapshot, WindVector ambientWind,
                                  HurricaneCategory nextCategory, float intensificationStrength) {
        this.position = new Vec3(snapshot.centerX(), level.getSeaLevel(), snapshot.centerZ());
        this.cycloneRadius = snapshot.radius();
        this.cycloneIntensity = Mth.clamp(snapshot.intensity(), 0.0F, 1.0F);
        this.radius = Mth.clamp(snapshot.radius() * 0.18F, 38.0F, 64.0F);
        this.category = nextCategory;
        this.targetDestructiveStrength = Mth.clamp(
                this.cycloneIntensity * 0.60F + intensificationStrength * 0.40F,
                0.0F,
                1.0F
        );
        this.rebuildLifecycleTimings();

        float boostedBase = Math.max(ambientWind.baseSpeed(), 11.0F + this.targetDestructiveStrength * 22.0F);
        float boostedGust = Math.max(ambientWind.gustSpeed(), boostedBase + 6.0F + this.category.ordinal() * 3.0F);
        this.wind = new WindVector(boostedBase, ambientWind.angleRadians(), boostedGust);
        this.refreshAnchorY(level);
    }

    public float getLifetimeSeconds() {
        return this.ageTicks / 20.0F;
    }

    public UUID getId() {
        return this.id;
    }

    public int getAgeTicks() {
        return this.ageTicks;
    }

    public StormLifecyclePhase getPhase() {
        return this.phase;
    }

    public boolean isDebugSpawn() {
        return this.debugSpawn;
    }

    public boolean isLinkedToCyclone() {
        return this.cycloneId != null;
    }

    @Nullable
    public UUID getCycloneId() {
        return this.cycloneId;
    }

    public float getAnchorY() {
        return this.anchorY;
    }

    public float getCoreRadius() {
        float localCore = Math.max(this.radius * 7.8F, 320.0F + this.category.ordinal() * 52.0F);
        float cycloneDriven = Math.max(260.0F, this.cycloneRadius * 1.18F);
        return Math.max(localCore, cycloneDriven);
    }

    public float getStormExtentRadius() {
        float coreRadius = this.getCoreRadius();
        float cycloneDriven = Math.max(3600.0F, this.cycloneRadius * 12.0F) * OUTER_SHELL_SCALE;
        return Math.max(coreRadius * 2.75F, cycloneDriven + this.category.ordinal() * 120.0F);
    }

    public float getVisualEyeRadius() {
        float coreRadius = this.getCoreRadius();
        float ratio = 0.17F + this.category.ordinal() * 0.011F;
        return coreRadius * ratio;
    }

    public float getVisualEdgeFade() {
        return Math.max(this.getStormExtentRadius() * 0.055F, 64.0F);
    }

    public int getBandCount() {
        return 3 + Math.min(2, this.category.ordinal() / 2);
    }

    public float getBandWidth() {
        return Math.max(this.getCoreRadius() * 0.075F, 32.0F);
    }

    public float getSpiralTightness() {
        return 0.052F + this.category.ordinal() * 0.0060F;
    }

    public float getRotationSpeed() {
        int periodTicks = Math.max(12000, 14400 - this.category.ordinal() * 600);
        return (float) (Math.PI * 2.0D / (double) periodTicks);
    }

    public float getTransitionStart() {
        return Math.max(this.getVisualEyeRadius() + this.getBandWidth() * 0.72F, this.getCoreRadius() * 0.28F);
    }

    public float getTransitionEnd() {
        return Math.max(this.getTransitionStart() + this.getBandWidth() * 6.0F, this.getStormExtentRadius() * 0.58F);
    }

    public float getRotationPhase() {
        return this.ageTicks * this.getRotationSpeed();
    }

    public Vec3 getRenderPosition(float partialTick) {
        return this.position;
    }

    public HurricaneRenderDescriptor getRenderDescriptor(float partialTick) {
        return HurricaneRenderDescriptor.create(
                Math.max(this.radius, 1.0F),
                this.getRenderIntensity(partialTick),
                this.category
        );
    }

    public float getRenderIntensity(float partialTick) {
        return Mth.clamp(this.destructiveStrength, 0.0F, 1.0F);
    }

    public float getVisualSpin(float partialTick) {
        return (this.ageTicks + partialTick) * this.getRotationSpeed();
    }

    public float getVisualSeed() {
        return (Math.abs(this.id.hashCode()) % 10000) / 10000.0F;
    }

    public HurricaneRenderSnapshot createRenderSnapshot() {
        return new HurricaneRenderSnapshot(
                this.id,
                this.position.x,
                this.position.z,
                this.getAnchorY(),
                this.getCoreRadius(),
                this.getStormExtentRadius(),
                this.getVisualEyeRadius(),
                this.getVisualEdgeFade(),
                this.getBandCount(),
                this.getBandWidth(),
                this.getSpiralTightness(),
                this.getRotationPhase(),
                this.getRotationSpeed(),
                this.getTransitionStart(),
                this.getTransitionEnd(),
                this.getRenderIntensity(0.0F),
                HURRICANE_CLOUD_TYPE_ID,
                this.ageTicks
        );
    }

    public CompoundTag toPersistentTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", this.id);
        if (this.cycloneId != null) {
            tag.putUUID("cycloneId", this.cycloneId);
        }
        tag.putBoolean("debugSpawn", this.debugSpawn);
        tag.putDouble("x", this.position.x);
        tag.putDouble("y", this.position.y);
        tag.putDouble("z", this.position.z);
        tag.putFloat("radius", this.radius);
        tag.putFloat("windSpeed", this.wind.baseSpeed());
        tag.putFloat("windAngle", this.wind.angleRadians());
        tag.putFloat("windGust", this.wind.gustSpeed());
        tag.putString("category", this.category.name());
        tag.putFloat("cycloneRadius", this.cycloneRadius);
        tag.putFloat("cycloneIntensity", this.cycloneIntensity);
        tag.putFloat("destructiveStrength", this.destructiveStrength);
        tag.putFloat("targetDestructiveStrength", this.targetDestructiveStrength);
        tag.putFloat("anchorY", this.anchorY);
        tag.putInt("ageTicks", this.ageTicks);
        tag.putInt("phaseTicks", this.phaseTicks);
        tag.putInt("formationTicks", this.formationTicks);
        tag.putInt("activeTicks", this.activeTicks);
        tag.putInt("dissipationTicks", this.dissipationTicks);
        tag.putString("phase", this.phase.name());
        return tag;
    }

    public static HurricaneInstance fromPersistentTag(CompoundTag tag) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        UUID cycloneId = tag.hasUUID("cycloneId") ? tag.getUUID("cycloneId") : null;
        Vec3 position = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        float radius = tag.getFloat("radius");
        WindVector wind = new WindVector(tag.getFloat("windSpeed"), tag.getFloat("windAngle"), tag.getFloat("windGust"));
        HurricaneCategory category = parseCategory(tag.getString("category"));
        boolean debugSpawn = !tag.contains("debugSpawn") || tag.getBoolean("debugSpawn");

        HurricaneInstance hurricane = new HurricaneInstance(id, cycloneId, position, radius, wind, category, debugSpawn);
        hurricane.applyPersistentState(tag);
        return hurricane;
    }

    private static HurricaneCategory parseCategory(String name) {
        if (name == null || name.isBlank()) {
            return HurricaneCategory.ONE;
        }
        try {
            return HurricaneCategory.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return HurricaneCategory.ONE;
        }
    }

    private static StormLifecyclePhase parsePhase(String name, StormLifecyclePhase fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return StormLifecyclePhase.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void applyPersistentState(CompoundTag tag) {
        this.cycloneRadius = tag.contains("cycloneRadius") ? tag.getFloat("cycloneRadius") : this.cycloneRadius;
        this.cycloneIntensity = tag.contains("cycloneIntensity") ? tag.getFloat("cycloneIntensity") : this.cycloneIntensity;
        this.destructiveStrength = tag.contains("destructiveStrength") ? tag.getFloat("destructiveStrength") : this.destructiveStrength;
        this.targetDestructiveStrength = tag.contains("targetDestructiveStrength") ? tag.getFloat("targetDestructiveStrength") : this.targetDestructiveStrength;
        this.anchorY = tag.contains("anchorY") ? tag.getFloat("anchorY") : this.anchorY;
        this.ageTicks = tag.getInt("ageTicks");
        this.phaseTicks = tag.getInt("phaseTicks");
        this.formationTicks = tag.contains("formationTicks") ? tag.getInt("formationTicks") : this.formationTicks;
        this.activeTicks = tag.contains("activeTicks") ? tag.getInt("activeTicks") : this.activeTicks;
        this.dissipationTicks = tag.contains("dissipationTicks") ? tag.getInt("dissipationTicks") : this.dissipationTicks;
        this.lastWindFieldTick = tag.contains("lastWindFieldTick") ? tag.getLong("lastWindFieldTick") : this.lastWindFieldTick;
        this.lastDestructionTick = tag.contains("lastDestructionTick") ? tag.getLong("lastDestructionTick") : this.lastDestructionTick;
        this.phase = parsePhase(tag.getString("phase"), this.phase);
    }

    public void tick(Level level) {
        if (level.isClientSide) {
            return;
        }

        this.ageTicks++;
        this.refreshAnchorY(level);
        this.tickLifecycle();
        ServerLevel serverLevel = (ServerLevel) level;
        if (this.phase.isTerminal()) {
            return;
        }
        long gameTime = serverLevel.getGameTime();
        HurricaneWindField.apply(this, serverLevel, gameTime);
        HurricaneDestructionManager.apply(this, serverLevel, gameTime);
    }

    float getDestructiveStrength() {
        return this.destructiveStrength;
    }

    float getWindIntensity() {
        return Mth.clamp(this.cycloneIntensity * 0.30F + this.destructiveStrength * 0.70F, 0.0F, 1.0F);
    }

    float getRotationDirection() {
        return (this.id.getLeastSignificantBits() & 1L) == 0L ? 1.0F : -1.0F;
    }

    boolean markWindFieldTick(long gameTime) {
        if (gameTime - this.lastWindFieldTick < WIND_FIELD_INTERVAL_TICKS) {
            return false;
        }
        this.lastWindFieldTick = gameTime;
        return true;
    }

    boolean markDestructionTick(long gameTime) {
        if (gameTime - this.lastDestructionTick < DESTRUCTION_INTERVAL_TICKS) {
            return false;
        }
        this.lastDestructionTick = gameTime;
        return true;
    }

    public void markDissipating() {
        if (this.phase == StormLifecyclePhase.DISSIPATING || this.phase == StormLifecyclePhase.DISSIPATED) {
            return;
        }
        this.phase = StormLifecyclePhase.DISSIPATING;
        this.phaseTicks = 0;
    }

    public void activateImmediately() {
        this.phase = StormLifecyclePhase.ACTIVE;
        this.phaseTicks = 0;
        this.destructiveStrength = this.targetDestructiveStrength;
    }

    public boolean isDead() {
        return this.phase.isTerminal();
    }

    private void tickLifecycle() {
        switch (this.phase) {
            case FORMING -> {
                float rate = 1.0F / Math.max(1, this.formationTicks);
                this.destructiveStrength = Math.min(this.targetDestructiveStrength, this.destructiveStrength + rate);
                if (this.phaseTicks >= this.formationTicks || this.destructiveStrength >= this.targetDestructiveStrength - 0.02F) {
                    this.phase = StormLifecyclePhase.ACTIVE;
                    this.phaseTicks = 0;
                }
            }
            case ACTIVE -> {
                this.destructiveStrength = Mth.lerp(0.06F, this.destructiveStrength, this.targetDestructiveStrength);
                if (this.phaseTicks >= this.activeTicks) {
                    this.phase = StormLifecyclePhase.DISSIPATING;
                    this.phaseTicks = 0;
                }
            }
            case DISSIPATING -> {
                float rate = 1.0F / Math.max(1, this.dissipationTicks);
                this.destructiveStrength = Math.max(0.0F, this.destructiveStrength - rate);
                if (this.phaseTicks >= this.dissipationTicks || this.destructiveStrength <= 0.02F) {
                    this.phase = StormLifecyclePhase.DISSIPATED;
                    this.destructiveStrength = 0.0F;
                }
            }
            case DISSIPATED -> this.destructiveStrength = 0.0F;
        }
        this.phaseTicks++;
    }

    private void rebuildLifecycleTimings() {
        float categoryBias = HurricaneCategory.values().length <= 1
                ? 0.0F
                : this.category.ordinal() / (float) (HurricaneCategory.values().length - 1);
        float persistenceFactor = Mth.clamp(
                this.targetDestructiveStrength * 0.70F + this.cycloneIntensity * 0.30F + categoryBias * 0.20F,
                0.0F,
                1.0F
        );
        this.formationTicks = Mth.floor(Mth.lerp(persistenceFactor, 20.0F * 10.0F, 20.0F * 26.0F));
        this.activeTicks = Mth.floor(Mth.lerp(persistenceFactor, 20.0F * 120.0F, 20.0F * 260.0F));
        this.dissipationTicks = Mth.floor(Mth.lerp(persistenceFactor, 20.0F * 16.0F, 20.0F * 52.0F));
    }
}

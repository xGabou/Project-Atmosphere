package net.Gabou.projectatmosphere.modules.hurricane;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneSnapshot;
import net.Gabou.projectatmosphere.modules.core.WindVector;
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

    private static final float DEFAULT_ANCHOR_Y = 384.0F;
    private static final float MIN_WORLD_ANCHOR_Y = 256.0F;
    private static final float CLOUD_LAYER_DESCENT = 200.0F;
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
    private float anchorY = DEFAULT_ANCHOR_Y;
    private int ageTicks;
    private long lastWindFieldTick = Long.MIN_VALUE;
    private long lastDestructionTick = Long.MIN_VALUE;

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
        this.destructiveStrength = 0.55F;
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
        this.anchorY = Math.max(MIN_WORLD_ANCHOR_Y, cloudHeight - CLOUD_LAYER_DESCENT);
    }

    public void updateFromCyclone(ServerLevel level, CycloneSnapshot snapshot, WindVector ambientWind,
                                  HurricaneCategory nextCategory, float intensificationStrength) {
        this.position = new Vec3(snapshot.centerX(), level.getSeaLevel(), snapshot.centerZ());
        this.cycloneRadius = snapshot.radius();
        this.cycloneIntensity = Mth.clamp(snapshot.intensity(), 0.0F, 1.0F);
        this.radius = Mth.clamp(snapshot.radius() * 0.18F, 38.0F, 64.0F);
        this.category = nextCategory;
        this.destructiveStrength = Mth.clamp(
                this.cycloneIntensity * 0.60F + intensificationStrength * 0.40F,
                0.0F,
                1.0F
        );

        float boostedBase = Math.max(ambientWind.baseSpeed(), 11.0F + this.destructiveStrength * 22.0F);
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
        float cycloneDriven = Math.max(4200.0F, this.cycloneRadius * 18.0F);
        return Math.max(coreRadius * 14.0F, cycloneDriven + this.category.ordinal() * 520.0F);
    }

    public float getVisualEyeRadius() {
        float coreRadius = this.getCoreRadius();
        float ratio = 0.17F + this.category.ordinal() * 0.011F;
        return coreRadius * ratio;
    }

    public float getVisualEdgeFade() {
        return Math.max(this.getStormExtentRadius() * 0.09F, 160.0F);
    }

    public int getBandCount() {
        return 3 + Math.min(2, this.category.ordinal() / 2);
    }

    public float getBandWidth() {
        return Math.max(this.getCoreRadius() * 0.145F, 52.0F);
    }

    public float getSpiralTightness() {
        return 0.052F + this.category.ordinal() * 0.0060F;
    }

    public float getRotationSpeed() {
        int periodTicks = Math.max(12000, 14400 - this.category.ordinal() * 600);
        return (float) (Math.PI * 2.0D / (double) periodTicks);
    }

    public float getTransitionStart() {
        return Math.max(this.getVisualEyeRadius() + this.getBandWidth() * 0.78F, this.getCoreRadius() * 0.30F);
    }

    public float getTransitionEnd() {
        return Math.max(this.getTransitionStart() + this.getBandWidth() * 18.0F, this.getStormExtentRadius() * 0.72F);
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
                HURRICANE_CLOUD_TYPE_ID,
                this.ageTicks
        );
    }

    public void tick(Level level) {
        if (level.isClientSide) {
            return;
        }

        this.ageTicks++;
        this.refreshAnchorY(level);
        ServerLevel serverLevel = (ServerLevel) level;
        long gameTime = serverLevel.getGameTime();
        HurricaneWindField.apply(this, serverLevel, gameTime);
        HurricaneDestructionManager.apply(this, serverLevel, gameTime);
    }

    float getDestructiveStrength() {
        return this.destructiveStrength;
    }

    float getWindIntensity() {
        return Mth.clamp(this.cycloneIntensity * 0.55F + this.destructiveStrength * 0.45F, 0.0F, 1.0F);
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
}

package net.Gabou.projectatmosphere.modules.hurricane;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneSnapshot;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.weather.StormShieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
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

        if (gameTime - this.lastWindFieldTick >= WIND_FIELD_INTERVAL_TICKS) {
            this.lastWindFieldTick = gameTime;
            this.applyWindField(serverLevel);
        }

        if (gameTime - this.lastDestructionTick >= DESTRUCTION_INTERVAL_TICKS) {
            this.lastDestructionTick = gameTime;
            this.applyDestruction(serverLevel);
        }
    }

    private void applyWindField(ServerLevel level) {
        double effectRadius = Math.min(this.getCoreRadius() * 1.08D, 196.0D);
        double eyeRadius = this.getVisualEyeRadius();
        double coreRadius = this.getCoreRadius();
        AABB box = new AABB(
                this.position.x - effectRadius, this.position.y - 8.0D, this.position.z - effectRadius,
                this.position.x + effectRadius, this.position.y + 112.0D, this.position.z + effectRadius
        );

        for (Entity entity : level.getEntities(null, box)) {
            if (entity == null || entity.isSpectator() || StormShieldManager.isProtected(level, entity.position())) {
                continue;
            }
            if (entity instanceof Player player && player.isCreative()) {
                continue;
            }

            double dx = entity.getX() - this.position.x;
            double dz = entity.getZ() - this.position.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < 1.0D || distSq > effectRadius * effectRadius) {
                continue;
            }

            double dist = Math.sqrt(distSq);
            double invDist = 1.0D / dist;
            float outerFactor = Mth.clamp((float) (1.0D - dist / effectRadius), 0.0F, 1.0F);
            float eyewallFactor = projectatmosphere$ringFactor((float) dist, (float) eyeRadius * 1.18F, (float) coreRadius * 0.92F);
            double tangentialStrength = (0.045D + this.category.ordinal() * 0.012D) * (0.35D + eyewallFactor * 0.65D);
            double inwardStrength = (0.018D + this.destructiveStrength * 0.050D) * (0.40D + outerFactor * 0.60D);
            double liftStrength = 0.008D + eyewallFactor * 0.055D;

            if (dist < eyeRadius * 0.92D) {
                tangentialStrength *= 0.30D;
                inwardStrength *= -0.10D;
                liftStrength *= 0.20D;
            }

            Vec3 tangential = new Vec3(-dz * invDist, 0.0D, dx * invDist).scale(tangentialStrength);
            Vec3 inward = new Vec3(-dx * invDist, 0.0D, -dz * invDist).scale(inwardStrength);
            Vec3 motion = tangential.add(inward).add(0.0D, liftStrength, 0.0D);

            entity.push(motion.x, motion.y, motion.z);
            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }
        }
    }

    private void applyDestruction(ServerLevel level) {
        if (this.destructiveStrength < 0.40F) {
            return;
        }

        RandomSource random = level.random;
        float eyeRadius = this.getVisualEyeRadius();
        float minRadius = eyeRadius * 1.30F;
        float maxRadius = Math.min(this.getCoreRadius() * 0.94F, 128.0F + this.category.ordinal() * 24.0F);
        int samples = 10 + this.category.ordinal() * 6;

        for (int i = 0; i < samples; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float sampleRadius = Mth.lerp(random.nextFloat(), minRadius, maxRadius);
            int x = Mth.floor(this.position.x + Math.cos(angle) * sampleRadius);
            int z = Mth.floor(this.position.z + Math.sin(angle) * sampleRadius);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            this.damageSurface(level, pos, random);
        }
    }

    private void damageSurface(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isLoaded(pos) || StormShieldManager.isProtected(level, pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getFluidState().is(FluidTags.WATER)) {
            return;
        }

        if (projectatmosphere$isSurfaceSoil(state)) {
            if (!state.is(Blocks.DIRT)) {
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                return;
            }
            if (this.destructiveStrength > 0.72F && random.nextFloat() < 0.12F) {
                level.destroyBlock(pos, false);
            }
            return;
        }

        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            this.destroyNearbyTreePieces(level, pos, random);
            return;
        }

        if (projectatmosphere$isWeakStructure(state) && random.nextFloat() < 0.65F + this.destructiveStrength * 0.20F) {
            level.destroyBlock(pos, false);
        }
    }

    private void destroyNearbyTreePieces(ServerLevel level, BlockPos origin, RandomSource random) {
        int limit = 2 + this.category.ordinal() * 2;
        int destroyed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1 && destroyed < limit; dx++) {
            for (int dz = -1; dz <= 1 && destroyed < limit; dz++) {
                for (int dy = 0; dy <= 6 && destroyed < limit; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(cursor) || StormShieldManager.isProtected(level, cursor)) {
                        continue;
                    }
                    BlockState candidate = level.getBlockState(cursor);
                    if (!candidate.is(BlockTags.LOGS) && !candidate.is(BlockTags.LEAVES)) {
                        continue;
                    }
                    if (random.nextFloat() > 0.70F + this.destructiveStrength * 0.15F) {
                        continue;
                    }
                    level.destroyBlock(cursor, false);
                    destroyed++;
                }
            }
        }
    }

    private static boolean projectatmosphere$isSurfaceSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.MUD);
    }

    private static boolean projectatmosphere$isWeakStructure(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS)
                || state.getBlock() instanceof AbstractGlassBlock;
    }

    private static float projectatmosphere$ringFactor(float radius, float innerRadius, float outerRadius) {
        if (outerRadius <= innerRadius) {
            return 0.0F;
        }
        float mid = (innerRadius + outerRadius) * 0.5F;
        float span = Math.max(1.0F, (outerRadius - innerRadius) * 0.5F);
        float normalized = 1.0F - Math.abs(radius - mid) / span;
        return Mth.clamp(normalized, 0.0F, 1.0F);
    }
}

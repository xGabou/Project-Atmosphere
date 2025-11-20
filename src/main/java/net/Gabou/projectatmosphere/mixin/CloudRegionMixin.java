package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.api.common.cloud.region.ITornadoRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.ScAPICloudRegionTornadoAccess;
import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.util.ICloudRegionId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Project Atmosphere + SimpleClouds integration.
 * Adds biome-based growth/shrink dynamics, adaptive lifetime, and a persistent unique ID.
 */
@Mixin(value = CloudRegion.class, remap = false)
public abstract class CloudRegionMixin implements ICloudRegionId, ITornadoRegion, ScAPICloudRegionTornadoAccess {

    // ----------------------------------------------------------------
    // Unique ID System (original Project Atmosphere integration)
    // ----------------------------------------------------------------

    @Unique
    private int projectatmosphere$id;

    @Unique
    private static final RandomSource PROJECTATMOSPHERE$RANDOM = RandomSource.create();

    @Unique
    private List<TornadoDescriptor> projectatmosphere$tornadoes = new ArrayList<>();

    @Unique
    private float projectatmosphere$prevTickX;

    @Unique
    private float projectatmosphere$prevTickZ;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void projectatmosphere$init(ResourceLocation cloudTypeId,
                                        Vec2 movementDirection,
                                        float maxSpeed,
                                        float accelerationFactor,
                                        float posX,
                                        float posZ,
                                        float radius,
                                        float rotation,
                                        float stretchFactor,
                                        int existsForTicks,
                                        int growTicks,
                                        int orderWeight,
                                        CallbackInfo ci) {
        this.projectatmosphere$id = PROJECTATMOSPHERE$RANDOM.nextInt();
        this.projectatmosphere$initialExistsForTicks = existsForTicks;
        this.projectatmosphere$initTornadoContainer(posX, posZ);
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void projectatmosphere$loadId(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("projectatmosphere_id")) {
            this.projectatmosphere$id = tag.getInt("projectatmosphere_id");
        } else {
            this.projectatmosphere$id = PROJECTATMOSPHERE$RANDOM.nextInt();
        }
        if (tag.contains("projectatmosphere_initial_exists_for_ticks")) {
            this.projectatmosphere$initialExistsForTicks = tag.getInt("projectatmosphere_initial_exists_for_ticks");
        } else {
            this.projectatmosphere$initialExistsForTicks = this.existsForTicks;
        }
        this.projectatmosphere$lifetimeAdjustment = tag.getInt("projectatmosphere_lifetime_adjustment");
        this.projectatmosphere$radiusMultiplier = tag.contains("projectatmosphere_radius_multiplier")
                ? tag.getFloat("projectatmosphere_radius_multiplier") : 1.0F;
        this.projectatmosphere$readTornadoesFromTag(tag);
    }

    @Inject(method = "toTag", at = @At("RETURN"))
    private void projectatmosphere$saveId(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putInt("projectatmosphere_id", this.projectatmosphere$id);
        tag.putInt("projectatmosphere_initial_exists_for_ticks", this.projectatmosphere$initialExistsForTicks);
        tag.putInt("projectatmosphere_lifetime_adjustment", this.projectatmosphere$lifetimeAdjustment);
        tag.putFloat("projectatmosphere_radius_multiplier", this.projectatmosphere$radiusMultiplier);
        this.projectatmosphere$writeTornadoesToTag(tag);
    }

    @Override
    public int projectatmosphere$getId() {
        return this.projectatmosphere$id;
    }

    @Override
    public List<TornadoDescriptor> getTornadoes() {
        return this.projectatmosphere$tornadoes;
    }

    @Override
    public void setTornadoes(List<TornadoDescriptor> descriptors) {
        if (descriptors == null) {
            this.projectatmosphere$tornadoes = new ArrayList<>();
        } else {
            this.projectatmosphere$tornadoes = descriptors;
        }
    }

    // ----------------------------------------------------------------
    // Biome-based growth + lifetime logic
    // ----------------------------------------------------------------

    @Shadow private float radius;
    @Shadow private float radiusO;
    @Shadow private float initialRadius;
    @Shadow private int existsForTicks;
    @Shadow private int growTicks;
    @Shadow private float posX;
    @Shadow private float posZ;
    @Shadow protected ResourceLocation cloudTypeId;

    @Unique private static final float MAX_RADIUS_MULTIPLIER = 1.3F;
    @Unique private static final float MIN_RADIUS_MULTIPLIER = 0.7F;
    @Unique private static final float RADIUS_ADJUST_RATE = 0.0025F;
    @Unique private static final int LIFETIME_ADJUST_STEP = 1;
    @Unique private static final float MINUTES_PER_GAME_TICK = 1.0F / 1200.0F;
    @Unique private static final float MIN_MINUTES_FOR_ADJUSTMENT = 2.0F;
    @Unique private static final float MAX_MINUTES_FOR_ADJUSTMENT = 6.0F;

    @Unique private int projectatmosphere$initialExistsForTicks;
    @Unique private float projectatmosphere$radiusMultiplier = 1.0F;
    @Unique private float projectatmosphere$radiusMultiplierO = 1.0F;
    @Unique private int projectatmosphere$lifetimeAdjustment;
    @Unique private BlockPos projectatmosphere$biomeFocusPos;
    @Unique private float projectatmosphere$focusMinutes;

    @Inject(method = "tick", at = @At("TAIL"))
    private void projectatmosphere$applyBiomeGrowth(RandomSource random, Level level, boolean isVisible, float speed, CallbackInfo ci) {
        if (level == null || !isVisible) return;
        this.projectatmosphere$applyBiomeDynamics(level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void projectatmosphere$cachePrevPositions(RandomSource random, Level level, boolean isVisible, float speed, CallbackInfo ci) {
        this.projectatmosphere$prevTickX = this.posX;
        this.projectatmosphere$prevTickZ = this.posZ;
    }

    @Inject(method = "tick", at = @At("TAIL"), cancellable = false)
    private void projectatmosphere$advanceTornadoes(RandomSource random, Level level, boolean isVisible, float speed, CallbackInfo ci) {
        float deltaX = this.posX - this.projectatmosphere$prevTickX;
        float deltaZ = this.posZ - this.projectatmosphere$prevTickZ;
        for (TornadoDescriptor descriptor : this.projectatmosphere$tornadoes) {
            descriptor.tick(deltaX, deltaZ);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"))
    private void projectatmosphere$readTornadoesFromPacket(FriendlyByteBuf buf, CallbackInfo ci) {
        this.projectatmosphere$tornadoes = this.projectatmosphere$readTornadoes(buf);
        this.projectatmosphere$initTornadoContainer(this.posX, this.posZ);
    }

    @Inject(method = "toPacket(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"))
    private void projectatmosphere$writeTornadoesToPacket(FriendlyByteBuf buf, CallbackInfo ci) {
        this.projectatmosphere$writeTornadoes(buf);
    }

    @Inject(method = "setRadius", at = @At("TAIL"))
    private void projectatmosphere$resetMultiplier(float radius, CallbackInfo ci) {
        this.projectatmosphere$radiusMultiplier = 1.0F;
        this.projectatmosphere$radiusMultiplierO = 1.0F;
    }

    @Unique
    private void projectatmosphere$applyBiomeDynamics(Level level) {
        BlockPos pos = BlockPos.containing(this.getWorldX(), level.getSeaLevel(), this.getWorldZ());
        Biome biome = level.getBiome(pos).value();
        float humidity = biome.getModifiedClimateSettings().downfall();
        float temperature = Mth.clamp(biome.getBaseTemperature() * 50.0F, -20.0F, 50.0F);
        if (!this.projectatmosphere$hasAccumulatedBiomeMinutes(pos, humidity)) {
            return;
        }

        boolean isHumid = humidity > 0.75F && temperature >= 0.0F && temperature <= 35.0F;
        boolean isArid = humidity < 0.25F || temperature > 35.0F;

        if (isHumid) {
            this.projectatmosphere$radiusMultiplier = Mth.clamp(
                    this.projectatmosphere$radiusMultiplier + RADIUS_ADJUST_RATE,
                    MIN_RADIUS_MULTIPLIER, MAX_RADIUS_MULTIPLIER
            );
            this.projectatmosphere$adjustLifetime(LIFETIME_ADJUST_STEP);
        } else if (isArid) {
            this.projectatmosphere$radiusMultiplier = Mth.clamp(
                    this.projectatmosphere$radiusMultiplier - RADIUS_ADJUST_RATE,
                    MIN_RADIUS_MULTIPLIER, MAX_RADIUS_MULTIPLIER
            );
            this.projectatmosphere$adjustLifetime(-LIFETIME_ADJUST_STEP);
        } else {
            this.projectatmosphere$radiusMultiplier = projectatmosphere$approach(
                    this.projectatmosphere$radiusMultiplier, 1.0F, RADIUS_ADJUST_RATE
            );
            this.projectatmosphere$relaxLifetime();
        }

        this.projectatmosphere$applyLifetimeBounds();

        // Apply modified radius
        this.radiusO = this.radius;
        this.radius *= this.projectatmosphere$radiusMultiplier;
    }

    @Unique
    // Track real minutes/days spent over the same region before letting the cloud react again.
    private boolean projectatmosphere$hasAccumulatedBiomeMinutes(BlockPos pos, float humidity) {
        if (this.projectatmosphere$biomeFocusPos == null || !this.projectatmosphere$biomeFocusPos.equals(pos)) {
            this.projectatmosphere$biomeFocusPos = pos;
            this.projectatmosphere$focusMinutes = 0.0F;
            return false;
        }
        this.projectatmosphere$focusMinutes += MINUTES_PER_GAME_TICK;
        float requiredMinutes = projectatmosphere$getMinutesForHumidity(humidity);
        if (this.projectatmosphere$focusMinutes < requiredMinutes) {
            return false;
        }
        this.projectatmosphere$focusMinutes -= requiredMinutes;
        return true;
    }

    @Unique
    private float projectatmosphere$getMinutesForHumidity(float humidity) {
        float humidityFactor = Mth.clamp(humidity, 0.0F, 1.0F);
        float inverted = 1.0F - humidityFactor;
        return MIN_MINUTES_FOR_ADJUSTMENT + inverted * (MAX_MINUTES_FOR_ADJUSTMENT - MIN_MINUTES_FOR_ADJUSTMENT);
    }

    @Unique
    private void projectatmosphere$adjustLifetime(int delta) {
        int limit = projectatmosphere$getLifetimeLimit();
        this.projectatmosphere$lifetimeAdjustment = Mth.clamp(this.projectatmosphere$lifetimeAdjustment + delta, -limit, limit);
    }

    @Unique
    private void projectatmosphere$relaxLifetime() {
        if (this.projectatmosphere$lifetimeAdjustment > 0)
            this.projectatmosphere$lifetimeAdjustment = Math.max(0, this.projectatmosphere$lifetimeAdjustment - LIFETIME_ADJUST_STEP);
        else if (this.projectatmosphere$lifetimeAdjustment < 0)
            this.projectatmosphere$lifetimeAdjustment = Math.min(0, this.projectatmosphere$lifetimeAdjustment + LIFETIME_ADJUST_STEP);
    }

    @Unique
    private void projectatmosphere$applyLifetimeBounds() {
        int limit = projectatmosphere$getLifetimeLimit();
        this.projectatmosphere$lifetimeAdjustment = Mth.clamp(this.projectatmosphere$lifetimeAdjustment, -limit, limit);
        int adjusted = this.projectatmosphere$initialExistsForTicks + this.projectatmosphere$lifetimeAdjustment;
        this.existsForTicks = Math.max(this.growTicks + 1, Math.max(1, adjusted));
    }

    @Unique
    private int projectatmosphere$getLifetimeLimit() {
        return Math.max(20, (int)(this.projectatmosphere$initialExistsForTicks * 0.3F));
    }

    @Unique
    private static float projectatmosphere$approach(float current, float target, float step) {
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return current;
    }

    @Unique
    private List<TornadoDescriptor> projectatmosphere$readTornadoes(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<TornadoDescriptor> descriptors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            descriptors.add(TornadoDescriptor.read(buf));
        }
        return descriptors;
    }

    @Unique
    private void projectatmosphere$writeTornadoes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.projectatmosphere$tornadoes.size());
        for (TornadoDescriptor descriptor : this.projectatmosphere$tornadoes) {
            descriptor.write(buf);
        }
    }

    @Unique
    private void projectatmosphere$writeTornadoesToTag(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (TornadoDescriptor descriptor : this.projectatmosphere$tornadoes) {
            listTag.add(descriptor.toTag());
        }
        tag.put(ITornadoRegion.TORNADO_LIST_KEY, listTag);
    }

    @Unique
    private void projectatmosphere$readTornadoesFromTag(CompoundTag tag) {
        this.projectatmosphere$tornadoes = new ArrayList<>();
        if (tag.contains(ITornadoRegion.TORNADO_LIST_KEY, Tag.TAG_LIST)) {
            ListTag listTag = tag.getList(ITornadoRegion.TORNADO_LIST_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                this.projectatmosphere$tornadoes.add(TornadoDescriptor.fromTag(listTag.getCompound(i)));
            }
        }
        this.projectatmosphere$initTornadoContainer(this.posX, this.posZ);
    }

    @Unique
    private void projectatmosphere$initTornadoContainer(float posX, float posZ) {
        if (this.projectatmosphere$tornadoes == null) {
            this.projectatmosphere$tornadoes = new ArrayList<>();
        }
        this.projectatmosphere$prevTickX = posX;
        this.projectatmosphere$prevTickZ = posZ;
    }

    // Shadows required to access position and transformations
    @Shadow public abstract float getWorldX();
    @Shadow public abstract float getWorldZ();
}

package net.Gabou.projectatmosphere.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

public class SmallNormalCloud1Entity extends Entity implements GeoAnimatable {
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(SmallNormalCloud1Entity.class, EntityDataSerializers.FLOAT);
    private static final float DEFAULT_SIZE = 10.0f;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SmallNormalCloud1Entity(EntityType<? extends SmallNormalCloud1Entity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setSize(DEFAULT_SIZE*this.getSize());
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SIZE, DEFAULT_SIZE);
    }

    public void setSize(float size) {
        this.entityData.set(SIZE, size);
    }

    public float getSize() {
        return this.entityData.get(SIZE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            Vec3 motion = this.getDeltaMovement();
            double driftX = (this.random.nextDouble() - 0.5) * 0.002;
            double driftZ = (this.random.nextDouble() - 0.5) * 0.002;
            this.setDeltaMovement(motion.add(driftX, 0, driftZ));
            this.move(MoverType.SELF,this.getDeltaMovement());
        }
        if (this.tickCount > 20 * 60 * 10) { // 10 minutes
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setSize(tag.getFloat("Size"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Size", this.getSize());
    }
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this,"controller",0, this::predicate));
    }
    private  <E extends GeoAnimatable> PlayState predicate(final AnimationState<E> state) {
        state.getController().setAnimation(RawAnimation.begin());
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object blockEntity) {
        return RenderUtils.getCurrentTick();
    }

}

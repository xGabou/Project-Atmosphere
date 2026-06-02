package dev.protomanly.pmweather.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Entity.MovementEmission;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public class MovingBlock extends Entity {
   public static EntityDataAccessor<BlockPos> DATA_START_POS;
   public static EntityDataAccessor<BlockState> DATA_BLOCK_STATE;

   public MovingBlock(EntityType<? extends MovingBlock> entityType, Level level) {
      super(entityType, level);
      this.setBlockState(Blocks.STONE.defaultBlockState());
      this.setStartPos(BlockPos.ZERO);
   }

   public MovingBlock(EntityType<? extends MovingBlock> entityType, Level level, BlockState blockstate, BlockPos startPos) {
      super(entityType, level);
      this.setBlockState(blockstate);
      this.setStartPos(startPos);
   }

   public void tick() {
      super.tick();
      this.applyGravity();
      this.move(MoverType.SELF, this.getDeltaMovement());
      Vec3 motion = this.getDeltaMovement();
      if (!this.level().isClientSide()) {
         if (this.tickCount > 3600 || this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), (double)96.0F, false) == null) {
            this.discard();
            return;
         }

         if (this.onGround() && this.tickCount > 40) {
            if (this.level().getBlockState(this.blockPosition()).isAir()) {
               this.level().setBlockAndUpdate(this.blockPosition(), this.getBlockState());
            }

            this.discard();
         }
      }

      if (this.onGround()) {
         BlockPos c = this.blockPosition();
         BlockPos n = c.north(2);
         BlockPos e = c.east(2);
         BlockPos s = c.south(2);
         BlockPos w = c.west(2);
         n = this.level().getHeightmapPos(Types.MOTION_BLOCKING, n);
         e = this.level().getHeightmapPos(Types.MOTION_BLOCKING, e);
         s = this.level().getHeightmapPos(Types.MOTION_BLOCKING, s);
         w = this.level().getHeightmapPos(Types.MOTION_BLOCKING, w);
         if (n.getY() < c.getY()) {
            c = n;
         }

         if (e.getY() < c.getY()) {
            c = e;
         }

         if (s.getY() < c.getY()) {
            c = s;
         }

         if (w.getY() < c.getY()) {
            c = w;
         }

         Vec3 off = this.getPosition(1.0F).subtract(c.getCenter()).multiply((double)1.0F, (double)0.0F, (double)1.0F);
         motion = motion.add(off.multiply((double)0.05F, (double)0.0F, (double)0.05F).multiply((double)1.0F, (double)0.0F, (double)1.0F));
      }

      this.setDeltaMovement(motion.multiply((double)0.99F, (double)0.99F, (double)0.99F));
   }

   public void setStartPos(BlockPos pos) {
      this.entityData.set(DATA_START_POS, pos);
   }

   public BlockPos getStartPos() {
      return (BlockPos)this.entityData.get(DATA_START_POS);
   }

   public void setBlockState(BlockState state) {
      this.entityData.set(DATA_BLOCK_STATE, state);
   }

   public BlockState getBlockState() {
      return (BlockState)this.entityData.get(DATA_BLOCK_STATE);
   }

   protected Entity.MovementEmission getMovementEmission() {
      return MovementEmission.NONE;
   }

   public boolean canBeCollidedWith() {
      return false;
   }

   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < (double)327680.0F;
   }

   public boolean isAttackable() {
      return false;
   }

   public boolean isPickable() {
      return false;
   }

   protected double getDefaultGravity() {
      return 0.04;
   }

   public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
      return false;
   }

   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      builder.define(DATA_START_POS, BlockPos.ZERO);
      builder.define(DATA_BLOCK_STATE, Blocks.STONE.defaultBlockState());
   }

   protected void readAdditionalSaveData(CompoundTag compoundTag) {
      this.setBlockState(NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), compoundTag.getCompound("blockstate")));
      this.setStartPos((BlockPos)NbtUtils.readBlockPos(compoundTag, "startPos").orElse(BlockPos.ZERO));
   }

   protected void addAdditionalSaveData(CompoundTag compoundTag) {
      compoundTag.put("blockstate", NbtUtils.writeBlockState(this.getBlockState()));
      compoundTag.put("startPos", NbtUtils.writeBlockPos(this.getStartPos()));
   }

   static {
      DATA_START_POS = SynchedEntityData.defineId(MovingBlock.class, EntityDataSerializers.BLOCK_POS);
      DATA_BLOCK_STATE = SynchedEntityData.defineId(MovingBlock.class, EntityDataSerializers.BLOCK_STATE);
   }
}

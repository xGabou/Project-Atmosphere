package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.util.ICloudRegionId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a persistent, network-synchronized unique ID to each CloudRegion.
 */
@Mixin(value = CloudRegion.class, remap = false)
public class CloudRegionMixin implements ICloudRegionId {

    @Unique
    private int projectatmosphere$id;

    @Unique
    private static final RandomSource PROJECTATMOSPHERE$RANDOM = RandomSource.create();

    // ------------------------------------------------------------
    // Constructor injection (normal creation)
    // ------------------------------------------------------------
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
    }

    // ------------------------------------------------------------
    // Constructor injection (load from NBT)
    // ------------------------------------------------------------
    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void projectatmosphere$loadId(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("projectatmosphere_id")) {
            this.projectatmosphere$id = tag.getInt("projectatmosphere_id");
        } else {
            this.projectatmosphere$id = PROJECTATMOSPHERE$RANDOM.nextInt();
        }
    }

    // ------------------------------------------------------------
    // NBT save hook
    // ------------------------------------------------------------
    @Inject(method = "toTag", at = @At("RETURN"))
    private void projectatmosphere$saveId(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putInt("projectatmosphere_id", this.projectatmosphere$id);
    }

    // ------------------------------------------------------------
    // Getter
    // ------------------------------------------------------------
    @Override
    public int projectatmosphere$getId() {
        return this.projectatmosphere$id;
    }
}

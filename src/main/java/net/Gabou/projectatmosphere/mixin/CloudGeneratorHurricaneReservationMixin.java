package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudTypeSource;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.spawning.SpawnInfo;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneSemantics;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = CloudGenerator.class, remap = false)
public abstract class CloudGeneratorHurricaneReservationMixin {
    @Shadow @Final protected CloudTypeSource cloudGetter;

    @Inject(method = "createRegion", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$blockHurricaneRegionCreation(SpawnInfo info, float playerX, float playerZ, float x, float z, RandomSource random, boolean growTime, CallbackInfoReturnable<Optional<CloudRegion>> cir) {
        Level level = HurricaneSemantics.resolveLevel(this.cloudGetter);
        if (level == null) {
            return;
        }

        if (HurricaneSemantics.intersectsReservation(level, x, z, SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS)) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "addCloud", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$blockCloudsInsideHurricane(CloudRegion region, CloudGenerator.Order order, CallbackInfoReturnable<Boolean> cir) {
        Level level = HurricaneSemantics.resolveLevel(this.cloudGetter);
        if (level == null) {
            return;
        }

        double padding = region.getWorldRadius() + SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS;
        if (HurricaneSemantics.intersectsReservation(level, region.getWorldX(), region.getWorldZ(), padding)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getCloudAtPosition", at = @At("RETURN"), cancellable = true)
    private void projectatmosphere$returnHurricaneReservationRegion(float x, float z, CallbackInfoReturnable<CloudRegion> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }

        Level level = HurricaneSemantics.resolveLevel(this.cloudGetter);
        if (level == null) {
            return;
        }

        CloudRegion reservation = HurricaneSemantics.getReservationRegionAt(
                level,
                x * (double)SimpleCloudsConstants.CLOUD_SCALE,
                z * (double)SimpleCloudsConstants.CLOUD_SCALE
        );
        if (reservation != null) {
            cir.setReturnValue(reservation);
        }
    }
}

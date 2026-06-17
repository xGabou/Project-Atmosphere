package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneSemanticSample;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneSemantics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CloudManager.class, remap = false)
public abstract class SimpleCloudsCloudManagerMixin<T extends Level> {
    @Shadow protected T level;

    @Shadow public abstract CloudType getCloudTypeForId(ResourceLocation id);

    @Inject(method = "getCloudTypeAtPosition", at = @At("RETURN"), cancellable = true)
    private void projectatmosphere$surfaceHurricaneType(float x, float z, CallbackInfoReturnable<Pair<CloudType, Float>> cir) {
        HurricaneSemanticSample sample = HurricaneSemantics.sampleBest(this.level, x * (double)SimpleCloudsConstants.CLOUD_SCALE, z * (double)SimpleCloudsConstants.CLOUD_SCALE);
        CloudType hurricaneType = this.getCloudTypeForId(sample.cloudTypeId());
        if (hurricaneType == null) {
            return;
        }

        if (sample.inEye()) {
            cir.setReturnValue(Pair.of(hurricaneType, 1.0F));
            return;
        }
        if (!sample.isPresent()) {
            return;
        }

        Pair<CloudType, Float> current = cir.getReturnValue();
        float currentCoverage = 1.0F - current.getRight();
        if (sample.coverage() >= currentCoverage || current.getLeft() == SimpleCloudsConstants.EMPTY) {
            cir.setReturnValue(Pair.of(hurricaneType, 1.0F - sample.coverage()));
        }
    }

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$forceHurricaneRain(float x, float y, float z, CallbackInfoReturnable<Float> cir) {
        HurricaneSemanticSample sample = HurricaneSemantics.sampleBest(this.level, x, z);
        if (sample.inEye()) {
            cir.setReturnValue(0.0F);
            return;
        }
        if (!sample.isPresent()) {
            return;
        }

        CloudType hurricaneType = this.getCloudTypeForId(sample.cloudTypeId());
        if (hurricaneType == null || !hurricaneType.weatherType().includesRain()) {
            return;
        }

        float stormStartY = sample.anchorY() + hurricaneType.stormStart() * SimpleCloudsConstants.CLOUD_SCALE;
        float verticalFade = 1.0F - Mth.clamp((y - stormStartY) / SimpleCloudsConstants.RAIN_VERTICAL_FADE, 0.0F, 1.0F);
        cir.setReturnValue(Mth.clamp(sample.rainStrength() * verticalFade, 0.0F, 1.0F));
    }

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$forceHurricanePrecipitation(BlockPos pos, CallbackInfoReturnable<Pair<Boolean, Biome.Precipitation>> cir) {
        if (!this.level.canSeeSky(pos) || this.level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
            cir.setReturnValue(Pair.of(false, Biome.Precipitation.NONE));
            return;
        }

        HurricaneSemanticSample sample = HurricaneSemantics.sampleBest(this.level, pos.getX() + 0.5D, pos.getZ() + 0.5D);
        if (sample.inEye()) {
            cir.setReturnValue(Pair.of(false, Biome.Precipitation.NONE));
            return;
        }
        if (!sample.isPresent()) {
            return;
        }

        CloudType hurricaneType = this.getCloudTypeForId(sample.cloudTypeId());
        if (hurricaneType == null || !hurricaneType.weatherType().includesRain()) {
            return;
        }

        float stormStartY = sample.anchorY() + hurricaneType.stormStart() * SimpleCloudsConstants.CLOUD_SCALE;
        if ((float)pos.getY() + 0.5F > stormStartY || sample.rainStrength() <= 0.12F) {
            cir.setReturnValue(Pair.of(false, Biome.Precipitation.NONE));
            return;
        }

        Biome.Precipitation precipitation = this.level.getBiome(pos).value().coldEnoughToSnow(pos)
                ? Biome.Precipitation.SNOW
                : Biome.Precipitation.RAIN;
        cir.setReturnValue(Pair.of(true, precipitation));
    }
}

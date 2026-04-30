package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.client.hurricane.ClientHurricaneStateCache.RenderableHurricane;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Represents one coherent hurricane cloud formation for the custom
 * volumetric render path. This is intentionally independent from
 * Simple Clouds {@code CloudRegion} so the storm remains one object
 * instead of a ring of separate cloud instances.
 */
public record HurricaneCloudVolume(
        UUID id,
        float centerX,
        float centerZ,
        float baseY,
        float height,
        float eyeRadius,
        float eyeClearRadius,
        float eyeSlope,
        float eyewallThickness,
        float canopyRadius,
        float shieldRadius,
        float canopyBaseFactor,
        float canopyTopFactor,
        float shieldBaseFactor,
        float shieldTopFactor,
        float bandStartRadius,
        float bandEndRadius,
        float bandWidth,
        float bandStrength,
        float bandCount,
        float fringeStrength,
        float spin,
        float intensity,
        float seed,
        Vec3 renderPosWorld,
        float cloudScale
) {
    public static HurricaneCloudVolume from(HurricaneInstance hurricane, float partialTick) {
        float scale = SimpleCloudsConstants.CLOUD_SCALE;
        Vec3 renderPos = hurricane.getRenderPosition(partialTick);
        HurricaneRenderDescriptor descriptor = hurricane.getRenderDescriptor(partialTick);

        return new HurricaneCloudVolume(
                hurricane.getId(),
                (float) renderPos.x / scale,
                (float) renderPos.z / scale,
                -descriptor.baseOffsetWorld() / scale,
                descriptor.volumeHeightWorld() / scale,
                descriptor.eyeRadiusWorld() / scale,
                descriptor.eyeClearRadiusWorld() / scale,
                descriptor.eyeSlope(),
                descriptor.eyewallThicknessWorld() / scale,
                descriptor.canopyRadiusWorld() / scale,
                descriptor.shieldRadiusWorld() / scale,
                descriptor.canopyBaseFactor(),
                descriptor.canopyTopFactor(),
                descriptor.shieldBaseFactor(),
                descriptor.shieldTopFactor(),
                descriptor.bandStartRadiusWorld() / scale,
                descriptor.bandEndRadiusWorld() / scale,
                descriptor.bandWidthWorld() / scale,
                descriptor.bandStrength(),
                descriptor.bandCount(),
                descriptor.fringeStrength(),
                hurricane.getVisualSpin(partialTick),
                Mth.clamp(hurricane.getRenderIntensity(partialTick), 0.0F, 1.0F),
                hurricane.getVisualSeed(),
                renderPos,
                scale
        );
    }

    public static HurricaneCloudVolume from(RenderableHurricane hurricane, float partialTick) {
        float scale = SimpleCloudsConstants.CLOUD_SCALE;
        Vec3 renderPos = new Vec3(hurricane.centerX() * scale, hurricane.anchorY(), hurricane.centerZ() * scale);
        float intensity = Mth.clamp(hurricane.intensity(), 0.0F, 1.0F);
        float seed = Mth.clamp(hurricane.seed(), 0.0F, 1.0F);
        float growth = Mth.lerp(intensity, 0.45F, 1.0F);
        float volumeHeight = Math.max(220.0F / scale, hurricane.bandWidth() * (0.42F + intensity * 0.18F));
        return new HurricaneCloudVolume(
                hurricane.id(),
                (float) hurricane.centerX(),
                (float) hurricane.centerZ(),
                0.0F,
                volumeHeight,
                hurricane.eyeRadius() * growth,
                hurricane.eyeRadius() * growth + hurricane.bandWidth() * (0.14F + intensity * 0.08F),
                0.90F + intensity * 0.12F,
                Math.max(hurricane.bandWidth() * (0.24F + intensity * 0.10F), 0.05F),
                hurricane.coreRadius() * Mth.lerp(intensity, 0.72F, 1.0F),
                hurricane.stormExtentRadius() * Mth.lerp(intensity, 0.80F, 1.0F),
                0.38F,
                0.82F,
                0.28F,
                0.96F,
                hurricane.transitionStart() * Mth.lerp(intensity, 0.72F, 1.0F),
                hurricane.transitionEnd() * Mth.lerp(intensity, 0.80F, 1.0F),
                hurricane.bandWidth() * (0.55F + intensity * 0.45F),
                Mth.clamp(intensity * 0.85F, 0.0F, 1.0F),
                hurricane.bandCount(),
                Mth.clamp(intensity * 0.80F + seed * 0.10F, 0.0F, 1.0F),
                hurricane.rotationPhase(),
                intensity,
                seed,
                renderPos,
                scale
        );
    }

    public Vec3 centerWorld() {
        return new Vec3(this.renderPosWorld.x, this.baseWorld() + this.heightWorld() * 0.5F, this.renderPosWorld.z);
    }

    public float boundsRadiusCloud() {
        return Math.max(this.shieldRadius * 1.20F, this.canopyRadius * 1.32F);
    }

    public float boundsRadiusWorld() {
        return this.boundsRadiusCloud() * this.cloudScale;
    }

    public float baseWorld() {
        return (float) this.renderPosWorld.y + this.baseY * this.cloudScale;
    }

    public float heightWorld() {
        return this.height * this.cloudScale;
    }

    public Vec3 boundsMinCloud() {
        return new Vec3(this.centerX - this.boundsRadiusCloud(), this.baseY - 2.0F, this.centerZ - this.boundsRadiusCloud());
    }

    public Vec3 boundsMaxCloud() {
        return new Vec3(this.centerX + this.boundsRadiusCloud(), this.baseY + this.height + 4.0F, this.centerZ + this.boundsRadiusCloud());
    }
}

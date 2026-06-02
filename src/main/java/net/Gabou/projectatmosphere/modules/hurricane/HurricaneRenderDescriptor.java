package net.Gabou.projectatmosphere.modules.hurricane;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

public record HurricaneRenderDescriptor(
        float baseOffsetWorld,
        float volumeHeightWorld,
        float eyeRadiusWorld,
        float eyeClearRadiusWorld,
        float eyeSlope,
        float eyewallThicknessWorld,
        float canopyRadiusWorld,
        float shieldRadiusWorld,
        float canopyBaseFactor,
        float canopyTopFactor,
        float shieldBaseFactor,
        float shieldTopFactor,
        float bandStartRadiusWorld,
        float bandEndRadiusWorld,
        float bandWidthWorld,
        float bandStrength,
        float bandCount,
        float fringeStrength
) {
    public static HurricaneRenderDescriptor create(float stormRadiusWorld, float intensity, HurricaneCategory category) {
        float normalizedIntensity = Mth.clamp(intensity, 0.0F, 1.0F);
        float categoryBias = category.ordinal() / (float) (HurricaneCategory.values().length - 1);
        float torusMajorRadius = Mth.clamp(
                stormRadiusWorld * (2.2F + normalizedIntensity * 0.80F + categoryBias * 0.45F),
                110.0F,
                360.0F
        );
        float torusMinorRadius = Mth.clamp(
                stormRadiusWorld * (0.72F + normalizedIntensity * 0.18F + categoryBias * 0.14F),
                32.0F,
                110.0F
        );
        float eyeRadius = Math.max(torusMajorRadius - torusMinorRadius * 1.16F, stormRadiusWorld * 0.55F);
        float eyeClearRadius = eyeRadius + torusMinorRadius * (0.32F + normalizedIntensity * 0.18F);
        float canopyRadius = torusMajorRadius;
        float shieldRadius = torusMajorRadius + torusMinorRadius * (2.10F + categoryBias * 0.60F);
        float bandStart = shieldRadius * (0.96F + normalizedIntensity * 0.04F);
        float bandEnd = shieldRadius * (1.78F + normalizedIntensity * 0.18F + categoryBias * 0.10F);
        float bandWidth = Mth.clamp(torusMinorRadius * (1.15F + normalizedIntensity * 0.30F), 48.0F, 180.0F);
        float bandStrength = Mth.clamp(0.22F + normalizedIntensity * 0.34F + categoryBias * 0.12F, 0.0F, 1.0F);
        float bandCount = 2.0F + category.ordinal();
        float fringeStrength = Mth.clamp(0.30F + normalizedIntensity * 0.34F + categoryBias * 0.12F, 0.0F, 1.0F);
        float baseOffset = 36.0F + normalizedIntensity * 44.0F + categoryBias * 12.0F;
        float volumeHeight = Mth.clamp(
                140.0F + torusMinorRadius * 1.35F + normalizedIntensity * 40.0F + categoryBias * 24.0F,
                150.0F,
                280.0F
        );
        float canopyBase = 0.30F - categoryBias * 0.02F;
        float canopyTop = 0.68F + normalizedIntensity * 0.04F;
        float shieldBase = 0.22F;
        float shieldTop = 0.78F;
        float eyeSlope = 0.94F + normalizedIntensity * 0.16F + categoryBias * 0.05F;
        float eyewallThickness = torusMinorRadius;

        return new HurricaneRenderDescriptor(
                baseOffset,
                volumeHeight,
                eyeRadius,
                eyeClearRadius,
                eyeSlope,
                eyewallThickness,
                canopyRadius,
                shieldRadius,
                canopyBase,
                canopyTop,
                shieldBase,
                shieldTop,
                bandStart,
                bandEnd,
                bandWidth,
                bandStrength,
                bandCount,
                fringeStrength
        );
    }

    public static HurricaneRenderDescriptor lerp(HurricaneRenderDescriptor from, HurricaneRenderDescriptor to, float partialTick) {
        float t = Mth.clamp(partialTick, 0.0F, 1.0F);
        return new HurricaneRenderDescriptor(
                Mth.lerp(t, from.baseOffsetWorld, to.baseOffsetWorld),
                Mth.lerp(t, from.volumeHeightWorld, to.volumeHeightWorld),
                Mth.lerp(t, from.eyeRadiusWorld, to.eyeRadiusWorld),
                Mth.lerp(t, from.eyeClearRadiusWorld, to.eyeClearRadiusWorld),
                Mth.lerp(t, from.eyeSlope, to.eyeSlope),
                Mth.lerp(t, from.eyewallThicknessWorld, to.eyewallThicknessWorld),
                Mth.lerp(t, from.canopyRadiusWorld, to.canopyRadiusWorld),
                Mth.lerp(t, from.shieldRadiusWorld, to.shieldRadiusWorld),
                Mth.lerp(t, from.canopyBaseFactor, to.canopyBaseFactor),
                Mth.lerp(t, from.canopyTopFactor, to.canopyTopFactor),
                Mth.lerp(t, from.shieldBaseFactor, to.shieldBaseFactor),
                Mth.lerp(t, from.shieldTopFactor, to.shieldTopFactor),
                Mth.lerp(t, from.bandStartRadiusWorld, to.bandStartRadiusWorld),
                Mth.lerp(t, from.bandEndRadiusWorld, to.bandEndRadiusWorld),
                Mth.lerp(t, from.bandWidthWorld, to.bandWidthWorld),
                Mth.lerp(t, from.bandStrength, to.bandStrength),
                Mth.lerp(t, from.bandCount, to.bandCount),
                Mth.lerp(t, from.fringeStrength, to.fringeStrength)
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.baseOffsetWorld);
        buf.writeFloat(this.volumeHeightWorld);
        buf.writeFloat(this.eyeRadiusWorld);
        buf.writeFloat(this.eyeClearRadiusWorld);
        buf.writeFloat(this.eyeSlope);
        buf.writeFloat(this.eyewallThicknessWorld);
        buf.writeFloat(this.canopyRadiusWorld);
        buf.writeFloat(this.shieldRadiusWorld);
        buf.writeFloat(this.canopyBaseFactor);
        buf.writeFloat(this.canopyTopFactor);
        buf.writeFloat(this.shieldBaseFactor);
        buf.writeFloat(this.shieldTopFactor);
        buf.writeFloat(this.bandStartRadiusWorld);
        buf.writeFloat(this.bandEndRadiusWorld);
        buf.writeFloat(this.bandWidthWorld);
        buf.writeFloat(this.bandStrength);
        buf.writeFloat(this.bandCount);
        buf.writeFloat(this.fringeStrength);
    }

    public static HurricaneRenderDescriptor read(FriendlyByteBuf buf) {
        return new HurricaneRenderDescriptor(
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat()
        );
    }
}

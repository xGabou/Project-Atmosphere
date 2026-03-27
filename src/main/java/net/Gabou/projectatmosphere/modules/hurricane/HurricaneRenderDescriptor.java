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
        float eyeRadius = stormRadiusWorld * (0.17F + normalizedIntensity * 0.11F + categoryBias * 0.04F);
        float eyeClearRadius = eyeRadius * (1.08F + normalizedIntensity * 0.10F);
        float eyewallThickness = Math.max(
                stormRadiusWorld * (0.36F + normalizedIntensity * 0.16F + categoryBias * 0.08F),
                eyeRadius * 0.80F
        );
        float canopyRadius = Mth.clamp(
                stormRadiusWorld * (3.0F + normalizedIntensity * 1.2F + categoryBias * 0.8F),
                220.0F,
                820.0F
        );
        float shieldRadius = canopyRadius * (1.10F + categoryBias * 0.12F);
        float bandStart = canopyRadius * (0.88F + normalizedIntensity * 0.10F);
        float bandEnd = shieldRadius * (1.02F + normalizedIntensity * 0.10F);
        float bandWidth = Mth.clamp(stormRadiusWorld * (0.42F + normalizedIntensity * 0.16F), 52.0F, 170.0F);
        float bandStrength = Mth.clamp(0.18F + normalizedIntensity * 0.30F + categoryBias * 0.12F, 0.0F, 1.0F);
        float bandCount = 2.0F + category.ordinal();
        float fringeStrength = Mth.clamp(0.28F + normalizedIntensity * 0.36F + categoryBias * 0.10F, 0.0F, 1.0F);
        float baseOffset = 44.0F + normalizedIntensity * 90.0F + categoryBias * 26.0F;
        float volumeHeight = Mth.clamp(
                210.0F + canopyRadius * 0.17F + normalizedIntensity * 95.0F + categoryBias * 50.0F,
                180.0F,
                460.0F
        );
        float canopyBase = 0.20F - categoryBias * 0.04F;
        float canopyTop = 0.95F + normalizedIntensity * 0.05F;
        float shieldBase = 0.14F;
        float shieldTop = 0.86F + normalizedIntensity * 0.05F;
        float eyeSlope = 0.82F + normalizedIntensity * 0.26F + categoryBias * 0.08F;

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

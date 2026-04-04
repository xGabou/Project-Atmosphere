package net.Gabou.projectatmosphere.compat.sky;

import net.minecraft.util.Mth;

public final class SkyConditionMath {
    private SkyConditionMath() {
    }

    public static float remapClamped(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        return Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
    }

    public static float reverseRemapClamped(float value, float start, float end) {
        return 1.0F - remapClamped(value, start, end);
    }

    public static float peakedFactor(float value, float center, float radius) {
        if (radius <= 0.0F) {
            return value == center ? 1.0F : 0.0F;
        }
        return Mth.clamp(1.0F - Math.abs(value - center) / radius, 0.0F, 1.0F);
    }
}
